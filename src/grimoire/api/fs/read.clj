(ns grimoire.api.fs.read
  "Filesystem datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.things :as t]
            [grimoire.api :as api]
            [grimoire.util :as util
             :refer [normalize-version]]
            [grimoire.either :refer [succeed result fail succeed?]]
            [grimoire.api.fs :as fs]
            [grimoire.api.fs.impl :as impl]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cemerick.url :as url]))

(defn- f->name [^java.io.File f]
  (url/url-decode (.getName f)))

;; List things
;;--------------------
(defmethod api/-list-groups ::fs/Config [config]
  (let [handle (io/file (:docs config))]
    (if (.isDirectory handle)
      (succeed
       (for [d     (.listFiles handle)
             :when (.isDirectory d)
             :when (.exists (io/file d "meta.edn"))]
         (t/->Group (url/url-decode (f->name d)))))
      (fail "Could not find store directory"))))

(defmethod api/-list-artifacts ::fs/Config [config thing]
  (let [thing  (t/ensure-thing thing)
        thing  (t/thing->group thing)
        _      (assert thing)
        handle (impl/thing->handle config :else thing)]
    (if (.isDirectory handle)
      (succeed
       (for [d     (.listFiles handle)
             :when (.isDirectory d)]
         (t/->Artifact thing (f->name d))))
      (->> thing
           t/thing->path
           (str "No such group ")
           fail))))

(defmethod api/-list-versions ::fs/Config [config thing]
  (let [thing    (t/ensure-thing thing)
        artifact (t/thing->artifact thing)
        _        (assert artifact)
        handle   (impl/thing->handle config :else artifact)]
    (if (.isDirectory handle)
      (->> (for [d     (reverse (sort (.listFiles handle)))
                 :when (.isDirectory d)]
             (t/->Version artifact (f->name d)))
           (sort-by (comp util/clojure-version->cmp-key t/thing->name))
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
      (succeed
       (for [d     (sort-by #(f->name %) (.listFiles handle))
             :when (.isDirectory d)]
         (t/->Platform version (f->name d))))
      (fail (str "No such version "
                 (t/thing->path thing))))))

(defmethod api/-list-namespaces ::fs/Config [config thing]
  (let [thing    (t/ensure-thing thing)
        _        (assert thing)
        platform (t/thing->platform thing)
        _        (assert platform)
        handle   (impl/thing->handle config :else platform)]
    (if (.isDirectory handle)
      (succeed
       (for [d     (.listFiles handle)
             :when (.isDirectory d)]
         (t/->Ns platform (f->name d))))
      (fail (str "No such platform "
                 (t/thing->path thing))))))

(defmethod api/-list-defs ::fs/Config [config thing]
  (let [thing     (t/ensure-thing thing)
        _         (assert thing)
        namespace (t/thing->namespace thing)
        _         (assert namespace)
        handle    (impl/thing->handle config :else namespace)]
    (if (.isDirectory handle)
      (succeed
       (for [d     (.listFiles handle)
             :when (.isDirectory d)]
         (t/->Def namespace (f->name d))))
      (fail (str "No such namespace "
                 (t/thing->path thing))))))

(defmethod api/-list-notes ::fs/Config [config thing]
  {:pre [(t/thing? thing)]}
  (let [lhs      (.toPath (io/file (:notes config)))]
    (if (t/versioned? thing)
      (let [versions (api/thing->prior-versions config thing)]
        (if (succeed? versions)
          (succeed
           (for [prior-thing (result versions)
                 :let  [v (t/thing->name (t/thing->version prior-thing))
                        h (impl/thing->notes-handle config prior-thing)]
                 :when (.exists h)
                 :when (.isFile h)]
             (let [rhs (.toPath h)
                   p   (.relativize lhs rhs)]
               (-> thing
                   (t/->Note (.getName h)
                             (.getPath h))
                   (assoc ::t/file (.toString p))))))

          ;; versions is a Fail, pass it down
          versions))

      (let [^java.io.File h (impl/thing->notes-handle config thing)]
        (if (.exists h)
          (let [rhs (.toPath h)
                p   (.relativize lhs rhs)]
            (succeed [(-> thing
                          (t/->Note (.getName h), (.getPath h))
                          (assoc ::t/file (.toString p)))]))
          (fail "No notes file!"))))))

(defmethod api/-list-examples ::fs/Config [config thing]
  {:pre [(t/thing? thing)]}
  (let [versions (api/thing->prior-versions config thing)
        lhs      (.toPath (io/file (:notes config)))]
    (if (succeed? versions)
      (succeed
       (for [prior-thing (result versions)
             :let        [v (t/thing->name (t/thing->version prior-thing))
                          h (impl/thing->handle config :examples prior-thing)]
             ex          (.listFiles h)
             :when       (.isFile ex)]
         (let [rhs (.toPath ex)
               p   (.relativize lhs rhs)]
           (-> thing
               (t/->Example (.getName ex)
                            (.getPath ex))
               (assoc ::t/file (.toString p))))))

      ;; versions is a Fail, pass it down
      versions)))

;; Read things
;;--------------------

(defmethod api/-read-note ::fs/Config [config thing]
  {:pre [(t/note? thing)]}
  (let [handle (io/file (t/thing->path thing))]
    (if (.exists handle) ;; guard against missing files
      (-> handle slurp succeed)
      (fail (str "No note for object "
                 (t/thing->path (t/thing->parent thing)))))))

(defmethod api/-read-example ::fs/Config [config thing]
  {:pre [(t/example? thing)]}
  (let [handle (io/file (:handle thing))]
    (if (.exists handle) ;; guard against missing files
      (-> handle slurp succeed)
      (fail (str "No such example! "
                 (:handle thing))))))

(defmethod api/-read-meta ::fs/Config [config thing]
  (let [thing  (t/ensure-thing thing)
        handle (impl/thing->meta-handle config thing)]
    (if (.exists handle) ;; guard against missing files
      (-> handle
          slurp
          (string/replace #"#<.*?>" "nil") ;; FIXME: Hack to ignore unreadable #<>s
          util/edn-read-string-with-readers
          succeed)
      (fail (str "No meta for object "
                 (t/thing->path thing))))))

(defmethod api/-list-related ::fs/Config [config thing]
  ;; FIXME: This assumes the old Grimoire 0.3.X related file format,
  ;; being a sequence of fully qualified symbols not Thing URIs. Will
  ;; work, but not optimal in terms of utility going forwards.
  ;;
  ;; As there are no datafiles which use this "related" format, it's
  ;; questionable whether preserving this format is a good idea or
  ;; not. I'm somewhat inclined to say not, but it's not high
  ;; priority.

  (let [thing           (t/ensure-thing thing)
        current-version (t/thing->version thing)
        versions        (api/thing->prior-versions config thing)]
    (if (succeed? versions)
      (succeed
       (for [thing (result versions)
             :let  [v (t/thing->name (t/thing->version thing))
                    h (impl/thing->related-handle config thing)]
             :when (.exists h)
             :when (.isFile h)
             line  (line-seq (io/reader h))
             :let  [sym (read-string line)]]
         (-> current-version
             (t/->Ns  (namespace sym))
             (t/->Def (name sym)))))

      ;; versions is a Fail, pass it down
      versions)))
