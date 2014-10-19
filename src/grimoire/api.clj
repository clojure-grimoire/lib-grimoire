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

(defn thing->group [thing]
  (if-not (isa? :group thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->artifact [thing]
  (if-not (isa? :artifact thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->version [thing]
  (if-not (isa? :version thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->namespace [thing]
  (if-not (isa? :namespace thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->path
  "Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."

  [thing]
  {:pre [(map? thing)]}
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

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------
(defn- thing->handle
  "Helper for grabbing handles for reading/writing.

  :docs     -> .json file
  :notes    -> .md file
  :examples -> dir"

  [{store :datastore} which thing]
  (let [d (get store which (:docs store))
        p (io/file (str d "/" (thing->path (:parent thing))))
        e (case which
            (:docs)     ".json"
            (:examples) nil
            (:notes)    ".md"
            :else       nil)
        n (if (= :def (:type thing))
            (util/munge (:name thing))
            (:name thing))
        h (io/file p (str n e))]
    (.mkdirs p)
    (when (= :examples which)
      (.mkdir h))
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

(defn read-docs [config thing]
  (let [thing  (ensure-thing thing)
        handle (thing->handle config :docs thing)]
    (->> handle io/reader json/parse-stream)))

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

(defn- thing->prior-versions
  "Returns a sequence of things representing itself at earlier or equal versions."

  [config thing]
  (let [thing    (ensure-thing thing)
        currentv (thing->version thing) ;; version handle
        current  (:name currentv)       ;; version string
        added    (:added (read-docs config thing)) ;; version string
        versions (list-versions config (:parent currentv))
        unv-path (thing->relative-path :version thing)]
    (for [v     versions
          :when (>= (semver/cmp v added) 0)
          :when (<= (semver/cmp v current) 0)]
      (path->thing (str (thing->path v) "/" unv-path)))))

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

(defn read-notes
  "Returns a sequence of pairs [version note-text] for all notes on
  prior or equal versions of the given thing."

  [config thing]
  (let [thing  (ensure-thing thing)]
    (for [thing (thing->prior-versions config thing)
          :let  [v (:name (thing->version thing))
                 h (thing->notes-handle config thing)]
          :when (.exists h)
          :when (.isFile h)]
      [v (slurp h)])))
