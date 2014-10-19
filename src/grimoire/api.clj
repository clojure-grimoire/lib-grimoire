(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking
  up examples, symbols, namespaces and artifacts as values without
  regard to the implementation of the datastore. Ports of Grimoire to
  different datastores should only need to tweak this namespace."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [cheshire.core :as json]
            [clj-semver.core :as semver]))

(defn isa? [t o]
  (= (:type o) t))

(defn ->T [t parent name]
  {:type   t
   :parent parent
   :name   name
   :uri    (str (:uri parent) 
                (when (:uri parent) "/")
                name)})

(defn thing->artifact [thing]
  (if-not (isa? :artifact thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->version [thing]
  (if-not (isa? :version thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->path
  "Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."

  [thing]
  (or (:uri thing)
      (->> thing
           (iterate :parent)
           (take-while identity)
           (reverse)
           (map :name)
           (interpose "/")
           (apply str))))

(defn path->thing [path]
  (->> (string/split path #"/")
       (map vector [:group :artifact :version :namespace :def])
       (reduce (fn [parent [t el]]
                 (->T t parent el))
               nil)))

(defn thing->relative-path [t thing]
  (->> thing
       (iterate :parent)
       (take-while #(not (= (:type %1) t)))
       (reverse)
       (map :name)
       (interpose "/")
       (apply str)))

(defn thing->root-to [t thing]
  (->> thing
       (iterate :parent)
       (take-while identity)
       (reverse)
       (take-while #(not (= (:type %1) t)))
       (map :name)
       (interpose "/")
       (apply str)))

(defn ensure-thing [maybe-thing]
  (cond (string? maybe-thing)
        ,,(thing->path maybe-thing)

        (map? maybe-thing)
        ,,maybe-thing

        :else
        ,,(throw (Exception.
                  (str "Unsupported ensure-thing value "
                       (pr-str maybe-thing))))))

;; Interacting with the datastore
;;--------------------------------------------------------------------
(defn- thing->handle
  "Helper for grabbing handles for reading/writing.

  :docs     -> .json file
  :notes    -> .md file
  :examples -> dir"

  [{store :datastore} which thing]
  (let [d (get store which)
        p (io/file (str d "/" (thing->path (:parent thing))))
        e (case which 
            (:docs)     ".json"
            (:examples) nil
            (:notes)    ".md")
        n (if (= :def (:type thing))
            (util/munge (:name thing))
            (name (:type thing)))
        h (io/file p (str n e))]
    (assert (.mkdirs p))
    (when (= :examples which)
      (assert (.mkdir h)))
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
