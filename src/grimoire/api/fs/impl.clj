(ns grimoire.api.fs.impl
  "Filesystem datastore implementation details. This namespace is not part of
  the intentional API exposed in `grimoire.api` and should not be used by
  library client code."
  (:require [grimoire.util :as util]
            [grimoire.things :as t]
            [grimoire.api.fs :refer [Config?]]
            [detritus.variants :as v]
            [clojure.java.io :as io]))

(defn file?
  [h]
  (instance? java.io.File h))

;; Private helpers for getting fs handles
;;--------------------

;; FIXME: munging needs to happen here
(defn thing->handle
  "Helper for grabbing handles for reading/writing.

  :meta     -> .edn file
  :related  -> .txt
  :notes    -> .md file
  :examples -> /examples/
  :else     -> /"

  [cfg which thing]
  {:pre  [(#{:meta :related :notes :examples :else} which)
          (t/thing? thing)
          (Config? cfg)]
   :post [(file? %)]}
  (let [d      (get cfg ({:meta     :docs
                          :else     :docs     ;; FIXME: is this really the default case? seems janky.
                          :related  :notes
                          :notes    :notes
                          :examples :examples}
                         which))
        parent (t/thing->parent thing)
        p      (io/file (str d "/" (when parent (t/thing->path parent))))
        n      (t/thing->name thing)
        e      (case which
                 (:meta)     "/meta.edn"
                 (:related)  "/related.txt"
                 (:examples) "/examples/"
                 (:notes)    "/notes.md"
                 nil)
        n      (if (= ::t/def (v/tag thing))
                 (util/munge (t/thing->name thing))
                 (t/thing->name thing))
        h      (io/file p (str n e))]
    h))

(defn thing->notes-handle
  "Helper for grabbing the handle of a notes file "

  [c thing]
  {:pre  [(Config? c)
          (t/thing? thing)]
   :post [(file? %)]}
  (thing->handle c :notes thing))

(defn thing->example-handle
  "Helper for getting a file handle for reading and writing a named example."

  [c thing name]
  {:pre  [(Config? c)
          (t/thing? thing)
          (string? name)]
   :post [(file? %)]}
  (let [h (thing->handle c :examples thing)]
    (io/file h (str name ".clj"))))

(defn thing->meta-handle
  "Helper for getting a file handle for reading and writing meta"

  [c thing]
  {:pre  [(Config? c)
          (t/thing? thing)]
   :post [(file? %)]}
  (let [h (thing->handle c :meta thing)]
    h))

(defn thing->related-handle
  "Helper for getting a file handle for reading and writing related files"

  [c thing]
  {:pre  [(Config? c)
          (t/thing? thing)]
   :post [(file? %)]}
  (let [h (thing->handle c :related thing)]
    h))
