(ns grimoire.api.fs
  "Filesystem datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.util :as util]
            [grimoire.things :refer :all]
            [grimoire.api :as api]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clj-semver.core :as semver]))

;; Private helpers for getting fs handles
;;--------------------
(defn- thing->handle
  "Helper for grabbing handles for reading/writing.

  :meta     -> .edn file
  :related  -> .txt
  :notes    -> .md file
  :examples -> dir"

  [{store :datastore} which thing]
  (let [which-store (if-not (= :notes which)
                      :docs :notes)
        d           (get store which (which-store store))
        parent      (:parent thing)
        p           (io/file (str d "/" (when parent (thing->path parent))))
        e           (case which
                      (:meta)     ".edn"
                      (:related)  "/related.txt"
                      (:examples) "/examples/"
                      (:notes)    "/extended-docstring.md"
                      nil)
        n           (if (= :def (:type thing))
                      (util/munge (:name thing))
                      (:name thing))
        h           (io/file p (str n e))]
    (.mkdirs p)
    (when (= :examples which)
      (when-not (.isDirectory h)
        (.mkdir h)))
    h))

(defn- thing->notes-handle
  "Helper for grabbing the handle of a notes file "

  [c thing]
  (thing->handle c :notes thing))

(defn- thing->example-handle
  "Helper for getting a file handle for reading and writing a named example."

  [c thing name]
  (let [h (thing->handle c :examples thing)]
    (io/file h (str name ".clj"))))

(defn- thing->meta-handle
  "Helper for getting a file handle for reading and writing meta"

  [c thing]
  (let [h (thing->handle c :meta thing)]
    h))

(defn- thing->related-handle
  "Helper for getting a file handle for reading and writing related files"

  [c thing]
  (let [h (thing->handle c :related thing)]
    h))

;; List things
;;--------------------
(defmethod api/list-groups :filesystem [config]
  (let [handle (io/file (-> config :datastore :docs))]
    (for [d (.listFiles handle)
          :when (.isDirectory d)]
      (->T :group nil (.getName d)))))

(defmethod api/list-artifacts :filesystem [config thing]
  (let [thing  (ensure-thing thing)
        thing  (thing->group thing)
        _      (assert thing)
        handle (thing->handle config :else thing)]
    (for [d (.listFiles handle)
          :when (.isDirectory d)]
      (->T :artifact thing (.getName d)))))

(defmethod api/list-versions :filesystem [config thing]
  (let [thing    (ensure-thing thing)
        artifact (thing->artifact thing)
        _        (assert artifact)
        handle   (thing->handle config :else artifact)]
    (for [d     (.listFiles handle)
          :when (.isDirectory d)]
      (->T :version artifact (.getName d)))))

(defmethod api/list-namespaces :filesystem [config thing]
  (let [thing   (ensure-thing thing)
        _       (assert thing)
        version (thing->version thing)
        _       (assert version)
        handle  (thing->handle config :else version)]
    (for [d     (.listFiles handle)
          :when (.isDirectory d)]
      (->T :namespace version (.getName d)))))

(defmethod api/list-defs :filesystem [config thing]
  (let [thing     (ensure-thing thing)
        _         (assert thing)
        namespace (thing->namespace thing)
        _         (assert namespace)
        handle    (thing->handle config :else namespace)]
    (for [d     (.listFiles handle)
          :when (.isFile d)]
      (->T :def namespace (string/replace (.getName d) #".edn" "")))))

;; FIXME: this should really be handled in data generation not in data use
(defn- normalize-version [x]
  (if-not (re-matches #"[0-9]+.[0-9]+.[0-9]+" x)
    (str x ".0")
    x))

(defmethod api/thing->prior-versions :filesystem [config thing]
  (let [thing    (ensure-thing thing)
        currentv (thing->version thing)               ; version handle
        current  (normalize-version (:name currentv)) ; version string
        added    (-> config
                     (api/read-meta thing)
                     (get :added "1.0.0")             ; FIXME: added in 1.0.0 by default. OK for core.
                     normalize-version)               ; version string
        versions (->> (:parent currentv)
                      (api/list-versions config))
        unv-path (thing->relative-path :version thing)]
    (for [v     versions
          :when (or (semver/newer? (:name v) added)
                    (semver/equal? (:name v) added))
          :when (or (semver/older? (:name v) current)
                    (semver/equal? (:name v) current))]
      ;; FIXME: this could be a direct constructor given an
      ;; appropriate vehicle for doing so since the type is directed
      ;; and single but may not generally make sense if this is not
      ;; the case.
      (path->thing (str (thing->path v) "/" unv-path)))))

;; Read things
;;--------------------
(defmethod api/read-notes :filesystem [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (api/thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->notes-handle config thing)]
          :when (.exists h)
          :when (.isFile h)]
      [v (slurp h)])))

(defmethod api/read-examples :filesystem [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (api/thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->handle config :examples thing)]
          ex    (.listFiles h)
          :when (.isFile ex)]
      [v (slurp ex)])))

(defmethod api/read-meta :filesystem [config thing]
  (let [thing  (ensure-thing thing)
        handle (thing->meta-handle config thing)]
    (when (.exists handle) ;; guard against missing files
      (-> handle
          slurp
          (string/replace #"#<.*?>" "nil") ;; FIXME: Hack to ignore unreadable #<>s
          edn/read-string))))

(defmethod api/read-related :filesystem [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (api/thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->related-handle config thing)]
          :when (.exists h)
          :when (.isFile h)
          line  (line-seq (io/reader h))]
      (path->thing line))))

;; Interacting with the datastore - writing
;;--------------------------------------------------------------------

;; FIXME: Remove this update when 1.7 drops
(defn update
  "λ [{A → B} A (λ [B args*] → C) args*] → {A → C}

  Updates a key in the map by applying f to the value at that key more
  arguments, returning the resulting map."

  [map key f & args]
  (assoc map key
         (apply f (get map key) args)))

(defmethod api/write-meta :filesystem [config thing data]
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        handle (thing->meta-handle config thing)
        _      (assert handle)]
    (spit handle (pr-str data))
    nil))

(defmethod api/write-notes :filesystem [config thing data]
  {:pre [(string? data)
         thing
         config
         (-> config :datastore :doc)]}
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        handle (thing->notes-handle config thing)
        _      (assert thing)]
    (spit handle data)))

;; FIXME: add write-example

(defmethod api/write-related :filesystem [config thing related-things]
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        _      (assert (isa? :def thing))
        handle (thing->related-handle config thing)
        _      (assert thing)]
    (doseq [thing related-things]
      (spit handle (str (thing->path thing) \newline)
            :append true))))
