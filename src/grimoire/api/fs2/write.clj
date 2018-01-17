(ns grimoire.api.fs2.write
  "Filesystem v2 datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.things :as t]
            [grimoire.api :as api]
            [grimoire.api.fs2 :as fs]
            [grimoire.api.fs2.impl :as impl]))

;; Interacting with the datastore - writing
;;--------------------------------------------------------------------
(defmethod api/-write-meta ::fs/Config [config thing data]
  {:pre [(fs/Config? config)
         (t/thing? thing)
         (map? data)]}
  (let [thing  (t/ensure-thing thing)
        _      (assert thing)
        handle (impl/thing->meta-handle config thing)
        _      (assert handle)]
    (.mkdirs (.getParentFile handle))
    (spit handle (pr-str data))
    nil))

(defmethod api/-write-note ::fs/Config [config thing data]
  {:pre [(fs/Config? config)
         (t/thing? thing)
         (string? data)]}
  (let [thing  (t/ensure-thing thing)
        _      (assert thing)
        handle (impl/thing->notes-handle config thing)
        _      (assert thing)]
    (.mkdirs (.getParentFile handle))
    (spit handle data)))

(defmethod api/-write-example ::fs/Config [config thing data]
  {:pre [(fs/Config? config)
         (t/thing? thing)
         (string? data)]}
  (let [thing  (t/ensure-thing thing)
        _      (assert thing)
        id     (java.util.UUID/randomUUID)
        handle (impl/thing->example-handle config thing id)
        _      (assert thing)]
    (.mkdirs (.getParentFile handle))
    (spit handle data)))


(defmethod api/-write-related ::fs/Config [config thing related-things]
  {:pre [(fs/Config? config)
         (t/thing? thing)
         (every? t/thing? related-things)]}
  (let [thing  (t/ensure-thing thing)
        _      (assert thing)
        _      (assert (t/def? thing))
        handle (impl/thing->related-handle config thing)
        _      (assert thing)]
    (.mkdirs (.getParentFile handle))
    (doseq [thing related-things]
      (spit handle (str (t/thing->path thing) \newline)
            :append true))))
