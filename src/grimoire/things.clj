(ns grimoire.things
  "This namespace implements a \"thing\" structure, approximating a URI, for
  uniquely naming and referencing entities in a Grimoire documentation
  store.

  Thing     ::= Sum[Group, Artifact, Version, Platform, Namespace, Def];
  Group     ::= Record[                   Name: String];
  Artifact  ::= Record[Parent: Group,     Name: String];
  Version   ::= Record[Parent: Artifact,  Name: String];
  Platform  ::= Record[Parent: Version,   Name: String];
  Namespace ::= Record[Parent: Platform,  Name: String];
  Def       ::= Record[Parent: Namespace, Name: String];"
  (:refer-clojure :exclude [isa?])
  (:require [clojure.string :as string]
            [grimoire.util :as u]))

(defn isa? [t o]
  (= (:type o) t))

(defn ->T [t parent name]
  {:type   t
   :parent parent
   :name   name
   ::uri    (str (::uri parent)
                 (when (::uri parent) "/")
                 name)})

;; Helpers for walking thing paths
;;--------------------------------------------------------------------
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

(defn thing->platform [thing]
  (if-not (isa? :platform thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->namespace [thing]
  (if-not (isa? :namespace thing)
    (when thing (recur (:parent thing)))
    thing))

;; Helpers for stringifying and reading paths
;;--------------------------------------------------------------------
(defn thing->path
  "Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."

  [thing]
  {:pre [(map? thing)]}
  (or (::uri thing)
      (->> thing
           (iterate :parent)
           (take-while identity)
           (reverse)
           (map :name)
           (interpose "/")
           (apply str))))

(defn ->Group
  ([groupid]
   {:pre [(string? groupid)]}
   (->T :group nil groupid))

  ([_ groupid]
   (->Group groupid)))

(defn ->Artifact
  [group artifact]
  {:pre [(and (map? group)
              (= :group (:type group)))
         (string? artifact)]}
  (->T :artifact group artifact))

(defn ->Version
  [artifact version]
  {:pre [(and (map? artifact)
              (= :artifact (:type artifact)))
         (string? version)]}
  (->T :version artifact version))

(defn ->Platform
  [version platform]
  {:pre [(and (map? version)
              (= :version (:type version)))
         (string? platform)]}
  (->T :platform version (u/normalize-platform platform)))

(defn ->Ns
  [platform namespace]
  {:pre [(and (map? platform)
              (= :platform (:type platform)))
         (string? namespace)]}
  (->T :namespace platform namespace))

(defn ->Def
  [namespace name]
  {:pre [(and (map? namespace)
              (= :namespace (:type namespace)))
         (string? name)]}
  (->T :def namespace name))

(defn path->thing [path]
  (->> (string/split path #"/")
       (map vector [:group :artifact :version :platform :namespace :def])
       (reduce (fn [acc [t v]]
                 (if v (->T t acc v) acc))
               nil)))

;; Manipulating things
;;--------------------------------------------------------------------
(defn thing->relative-path [t thing]
  (->> thing
       (iterate :parent)
       (take-while #(not= (:type %1) t))
       (reverse)
       (map :name)
       (interpose "/")
       (apply str)))

(defn thing->root-to [t thing]
  (->> thing
       (iterate :parent)
       (take-while identity)
       (reverse)
       (take-while #(not= (:type %1) t))
       (map :name)
       (interpose "/")
       (apply str)))

(defn ensure-thing [maybe-thing]
  (cond (string? maybe-thing)
        ,,(path->thing maybe-thing)

        (map? maybe-thing)
        ,,maybe-thing

        :else
        ,,(throw (Exception.
                  (str "Unsupported ensure-thing value "
                       (pr-str maybe-thing))))))
