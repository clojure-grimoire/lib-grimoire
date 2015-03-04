(ns grimoire.api.fs.read
  "Filesystem datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.things :as t]
            [grimoire.api :as api]
            [grimoire.util :refer [normalize-version]]
            [grimoire.either :refer [succeed result fail succeed?]]
            [grimoire.api.fs :as fs]
            [grimoire.api.fs.impl :as impl]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [detritus.variants :as v]
            [version-clj.core :as semver]))

;; List things
;;--------------------
(defmethod api/-list-groups ::fs/Config [config]
  (let [handle (io/file (:docs config))]
    (if (.isDirectory handle)
      (-> (for [d     (.listFiles handle)
                :when (.isDirectory d)]
            (t/->Group (.getName d)))
          succeed)
      (fail "Could not find store directory"))))

(defmethod api/-list-artifacts ::fs/Config [config thing]
  (let [thing  (t/ensure-thing thing)
        thing  (t/thing->group thing)
        _      (assert thing)
        handle (impl/thing->handle config :else thing)]
    (if (.isDirectory handle)
      (-> (for [d     (.listFiles handle)
                :when (.isDirectory d)]
            (t/->Artifact thing (.getName d)))
          succeed)
      (fail (str "No such group "
                 (t/thing->path thing))))))

(defmethod api/-list-versions ::fs/Config [config thing]
  (let [thing    (t/ensure-thing thing)
        artifact (t/thing->artifact thing)
        _        (assert artifact)
        handle   (impl/thing->handle config :else artifact)]
    (if (.isDirectory handle)
      (->> (for [d     (reverse (sort (.listFiles handle)))
                 :when (.isDirectory d)]
             (t/->Version artifact (.getName d)))
           (sort-by t/thing->name)
           reverse
           succeed)
      (fail (str "No such artifact "
                 (t/thing->path thing))))))

(defmethod api/-list-platforms ::fs/Config [config thing]
  (let [thing   (t/ensure-thing thing)
        _       (assert thing)
        version (t/thing->version thing)
        _       (assert version)
        handle  (impl/thing->handle config :else version)]
    (if (.isDirectory handle)
      (-> (for [d     (sort-by #(.getName %) (.listFiles handle))
                :when (.isDirectory d)]
            (t/->Platform version (.getName d)))
          succeed)
      (fail (str "No such version "
                 (t/thing->path thing))))))

(defmethod api/-list-namespaces ::fs/Config [config thing]
  (let [thing    (t/ensure-thing thing)
        _        (assert thing)
        platform (t/thing->platform thing)
        _        (assert platform)
        handle   (impl/thing->handle config :else platform)]
    (if (.isDirectory handle)
      (-> (for [d     (.listFiles handle)
                :when (.isDirectory d)]
            (t/->Ns platform (.getName d)))
          succeed)
      (fail (str "No such platform "
                 (t/thing->path thing))))))

(defmethod api/-list-defs ::fs/Config [config thing]
  (let [thing     (t/ensure-thing thing)
        _         (assert thing)
        namespace (t/thing->namespace thing)
        _         (assert namespace)
        handle    (impl/thing->handle config :else namespace)]
    (if (.isDirectory handle)
      (-> (for [d     (.listFiles handle)
                :when (.isDirectory d)]
            (t/->Def namespace (.getName d)))
          succeed)
      (fail (str "No such namespace "
                 (t/thing->path thing))))))

(defmethod api/-list-notes ::fs/Config [config thing]
  {:pre [(t/thing? thing)]}
  (if-not (t/versioned? thing)
    (let [versions (api/thing->prior-versions config thing)]
      (if (succeed? versions)
        (-> (for [thing (result versions)
                  :let  [v (t/thing->name (t/thing->version thing))
                         h (impl/thing->notes-handle config thing)]
                  :when (.exists h)
                  :when (.isFile h)]
              (t/->Note thing, (.getPath h)))
            succeed)

        ;; versions is a Fail, pass it down
        versions))

    (let [^java.io.File h (impl/thing->notes-handle config thing)]
      (succeed [(t/->Note thing, (.getPath h))]))))

(defmethod api/-list-examples ::fs/Config [config thing]
  {:pre [(t/thing? thing)]}
  (let [versions (api/thing->prior-versions config thing)]
    (if (succeed? versions)
      (-> (for [prior-thing (result versions)
                :let  [v (t/thing->name (t/thing->version prior-thing))
                       h (impl/thing->handle config :examples prior-thing)]
                ex    (.listFiles h)
                :when (.isFile ex)]
            (t/->Example thing, (.getPath ex)))
          succeed)

      ;; versions is a Fail, pass it down
      versions)))

;; Read things
;;--------------------

(defmethod api/-read-note ::fs/Config [config thing]
  (let [handle (impl/thing->notes-handle config (t/thing->parent thing))]
    (if (.exists handle) ;; guard against missing files
      (-> handle slurp succeed)
      (fail (str "No note for object "
                 (t/thing->path (t/thing->parent thing)))))))

;; FIXME: read-example

(defmethod api/-read-meta ::fs/Config [config thing]
  (let [thing  (t/ensure-thing thing)
        handle (impl/thing->meta-handle config thing)]
    (if (.exists handle) ;; guard against missing files
      (-> handle
          slurp
          (string/replace #"#<.*?>" "nil") ;; FIXME: Hack to ignore unreadable #<>s
          edn/read-string
          succeed)
      (fail (str "No meta for object "
                 (t/thing->path thing))))))

(defmethod api/-list-related ::fs/Config [config thing]
  ;; FIXME: This assumes the old Grimoire 0.3.X related file format,
  ;; being a sequence of fully qualified symbols not Thing URIs. Will
  ;; work, but not optimal in terms of utility going forwards.

  (let [thing           (t/ensure-thing thing)
        current-version (t/thing->version thing)
        versions        (api/thing->prior-versions config thing)]
    (if (succeed? versions)
      (-> (for [thing (result versions)
                :let  [v (t/thing->name (t/thing->version thing))
                       h (impl/thing->related-handle config thing)]
                :when (.exists h)
                :when (.isFile h)
                line  (line-seq (io/reader h))
                :let  [sym (read-string line)]]
            (-> current-version
                (t/->Ns  (namespace sym))
                (t/->Def (name sym))))
          succeed)

      ;; versions is a Fail, pass it down
      versions)))
