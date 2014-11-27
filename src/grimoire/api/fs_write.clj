(ns grimoire.api.fs-write
  "Filesystem datastore implementation of the Grimoire API."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.util :as util]
            [grimoire.things :refer :all]
            [grimoire.api :as api]
            [grimoire.api.fs :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clj-semver.core :as semver]))

;; Interacting with the datastore - writing
;;--------------------------------------------------------------------

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
