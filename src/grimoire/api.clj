(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking
  up examples, symbols, namespaces and artifacts as values without
  regard to the implementation of the datastore. Ports of Grimoire to
  different datastores should only need to tweak this namespace."

  (:refer-clojure :exclude [isa?])
  (:require [grimoire.util :as util]
            [grimoire.things :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [cheshire.core :as json]    ; FIXME: remove? in project.clj?
            [clj-semver.core :as semver]))

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------

;; Private helpers for getting fs handles
;;--------------------
(defn- thing->handle
  "Helper for grabbing handles for reading/writing.

  :meta     -> .edn file
  :related  -> .txt
  :notes    -> .md file
  :examples -> dir"

  [{store :datastore} which thing]
  (let [d (get store which (:docs store))
        p (io/file (str d "/" (thing->path (:parent thing))))
        e (case which
            (:meta)     ".edn"
            (:related)  ".txt"
            (:examples) nil
            (:notes)    ".md"
            nil)
        n (if (= :def (:type thing))
            (util/munge (:name thing))
            (:name thing))
        h (io/file p (str n e))]
    (.mkdirs p)
    (when (= :examples which)
      (when-not (.isDirectory h)
        (.mkdir h)))
    h))

(defn- thing->notes-handle
  "Helper for grabbing the handle of a notes file "

  [c thing]
  (let [h (thing->handle c :notes thing)]
    (io/file h "notes.md")))

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
(defn list-groups [config]
  (let [handle (io/file (-> config :datastore :docs))]
    (for [d (.listFiles handle)
          :when (.isDirectory d)]
      (->T :group nil (.getName d)))))

(defn list-artifacts [config thing]
  (let [thing  (ensure-thing thing)
        thing  (thing->group thing)
        _      (assert thing)
        handle (thing->handle config :else thing)]
    (for [d (.listFiles handle)
          :when (.isDirectory d)]
      (->T :artifact thing (.getName d)))))

(defn list-versions [config thing]
  (let [thing    (ensure-thing thing)
        artifact (thing->artifact thing)
        _        (assert artifact)
        handle   (thing->handle config :else artifact)]
    (for [d     (.listFiles handle)
          :when (.isDirectory d)]
      (->T :version artifact (.getName d)))))

(defn list-namespaces [config thing]
  (let [thing   (ensure-thing thing)
        _       (assert thing)
        version (thing->version thing)
        _       (assert version)
        handle  (thing->handle config :else version)]
    (for [d     (.listFiles handle)
          :when (.isDirectory d)]
      (->T :namespace version (.getName d)))))

(defn list-defs [config thing]
  (let [thing     (ensure-thing thing)
        _         (assert thing)
        namespace (thing->namespace thing)
        _         (assert namespace)
        handle    (thing->handle config :else namespace)]
    (for [d     (.listFiles handle)
          :when (.isFile d)]
      (->T :def namespace (string/replace (.getName d) #".clj" "")))))

(declare read-meta)

(defn- thing->prior-versions
  "Returns a sequence of things representing itself at earlier or equal versions."

  [config thing]
  (let [thing    (ensure-thing thing)
        currentv (thing->version thing) ;; version handle
        current  (:name currentv)       ;; version string
        added    (:added (read-meta config thing)) ;; version string
        versions (list-versions config (:parent currentv))
        unv-path (thing->relative-path :version thing)]
    (for [v     versions
          :when (>= (semver/cmp v added) 0)
          :when (<= (semver/cmp v current) 0)]
      (path->thing (str (thing->path v) "/" unv-path)))))

;; Read things
;;--------------------
(defn read-notes
  "Returns a sequence of pairs [version note-text] for all notes on
  prior or equal versions of the given thing."

  [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->handle config :notes thing)]
          :when (.exists h)
          :when (.isFile h)]
      [v (slurp h)])))

(defn read-examples
  "Returns a sequence of pairs [version example-text] for all examples on prior
  or equal versions of the given thing."

  [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->handle config :examples thing)]
          ex    (.listFiles h)
          :when (.isFile ex)]
      [v (slurp ex)])))

(defn read-meta [config thing]
  (let [thing  (ensure-thing thing)
        handle (thing->meta-handle config thing)]
    (->> handle slurp edn/read-string)))

(defn read-related
  "Returns a sequence of things representing symbols related to this or prior
  versions of the given symbol."

  [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (thing->prior-versions config thing)
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

(defn write-meta
  "Writes a map, being documentation data, into the datastore as specified by
  config at the def denoted by thing. The expectation of this operation is that
  you just take (meta the-var) and slam it right into write-meta

  Expected keys:
  - :ns       -> string naming the namespace
  - :name     -> string naming the symbol (unmunged)
  - :doc      -> documentation string
  - :arglists -> list of argument vectors
  - :src      -> string of source code
  - :added    -> string being a SemVer version
  - :column   -> integer being colunm number
  - :line     -> integer being line number
  - :file     -> string being file name
  - :type     -> one of #{:macro :fn :var :special}

  Note that this operation cleans the following keys:
  - :ns   - transformed from a Namespace to a String
  - :name - transformed from a Symbol to a String"

  [config thing data]
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        _      (assert (isa? :def thing))
        handle (thing->meta-handle config thing)
        _      (assert handle)
        data   (-> data
                   (update :ns ns-name)
                   (update :ns name)
                   (update :name name))]
    (spit handle (pr-str data))
    nil))

(defn write-notes
  "Writes a string into the datastore specified by the config at the path
  represented by thing. Note that thing need not be a def."

  [config thing data]
  {:pre [(string? data)
         thing
         config
         (-> config :datastore :doc)]}
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        handle (thing->handle config nil thing)
        _      (assert thing)]
    (spit handle data)))

;; FIXME: add write-example

(defn write-related
  "Writes a sequence of things representing defs into the datastore's
  related file as specified by the target thing."

  [config thing related-things]
  (let [thing  (ensure-thing thing)
        _      (assert thing)
        _      (assert (isa? :def thing))
        handle (thing->related-handle config thing)
        _      (assert thing)]
    (doseq [thing related-things]
      (spit handle (str (thing->path thing) \newline)
            :append true))))
