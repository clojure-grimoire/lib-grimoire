(ns grimoire.api.fs.read
  "Filesystem datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.things :refer :all]
            [grimoire.api :as api]
            [grimoire.api.fs :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [version-clj.core :as semver]))

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
    (->> (for [d     (reverse (sort (.listFiles handle)))
             :when (.isDirectory d)]
         (->T :version artifact (.getName d)))
      (sort-by :name)
      reverse)))

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
          :when (.isDirectory d)]
      (->T :def namespace (.getName d)))))

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
                   (get :added "0.0.0")
                   normalize-version)               ; version string
        versions (->> (:parent currentv)
                   (api/list-versions config))
        unv-path (thing->relative-path :version thing)]
    (for [v     versions
          :when (<= 0 (semver/version-compare (:name v) added))
          :when (>= 0 (semver/version-compare (:name v) current))]
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
  ;; FIXME: This assumes the old Grimoire 0.3.X related file format,
  ;; being a sequence of fully qualified symbols not Thing URIs. Will
  ;; work, but not optimal in terms of utility going forwards.

  (let [thing           (ensure-thing thing)
        current-version (thing->version thing)]
    (for [thing (api/thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->related-handle config thing)]
          :when (.exists h)
          :when (.isFile h)
          line  (line-seq (io/reader h))
          :let  [sym (read-string line)]]
      (-> current-version
        (->Ns  (namespace sym))
        (->Def (name sym))))))
