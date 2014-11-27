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
(defn thing->handle
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

(defn thing->notes-handle
  "Helper for grabbing the handle of a notes file "

  [c thing]
  (thing->handle c :notes thing))

(defn thing->example-handle
  "Helper for getting a file handle for reading and writing a named example."

  [c thing name]
  (let [h (thing->handle c :examples thing)]
    (io/file h (str name ".clj"))))

(defn thing->meta-handle
  "Helper for getting a file handle for reading and writing meta"

  [c thing]
  (let [h (thing->handle c :meta thing)]
    h))

(defn thing->related-handle
  "Helper for getting a file handle for reading and writing related files"

  [c thing]
  (let [h (thing->handle c :related thing)]
    h))

(defn update
  "λ [{A → B} A (λ [B args*] → C) args*] → {A → C}

  Updates a key in the map by applying f to the value at that key more
  arguments, returning the resulting map."

  [map key f & args]
  (assoc map key
         (apply f (get map key) args)))
