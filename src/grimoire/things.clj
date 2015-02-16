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
  (:refer-clojure :exclude [isa? def namespace])
  (:require [clojure.string :as string]
            [grimoire.util :as u]
            [detritus.variants :as v]))

(v/deftag group [name]
  {:pre [(string? name)]})

(v/deftag artifact [parent, name]
  {:pre [(group? parent)
         (string? name)]})

(v/deftag version [parent, name]
  {:pre [(artifact? parent)
         (string? name)]})

(v/deftag platform [parent, name]
  {:pre [(version? parent)
         (string? name)]})

(v/deftag namespace [parent, name]
  {:pre [(platform? parent)
         (string? name)]})

(v/deftag def [parent, name]
  {:pre [(namespace? parent)
         (string? name)]})

;; Helpers for walking thing paths
;;--------------------------------------------------------------------
(defn thing? [t]
  (or (group? t)
      (artifact? t)
      (version? t)
      (platform? t)
      (namespace? t)
      (def? t)))

(defn versioned? [t]
  (or (version? t)
      (artifact? t)
      (platform? t)
      (namespace? t)
      (def? t)))

(defn thing->parent [t]
  (when (thing? t)
    (:parent t)))

(defn thing->name [t]
  (when (thing? t)
    (:name t)))

(defn thing->group [t]
  {:pre [(thing? t)]}
  (if-not (group? t)
    (when t
      (recur (thing->parent t)))
    t))

(defn thing->artifact [t]
  {:pre [(thing? t)
         (or (artifact? t)
             (version? t)
             (platform? t)
             (namespace? t)
             (def? t))]}
  (if-not (artifact? t)
    (when t
      (recur (thing->parent t)))
    t))

(defn thing->version [t]
  {:pre [(thing? t)
         (or (version? t)
             (platform? t)
             (namespace? t)
             (def? t))]}
  (if-not (version? t)
    (when t
      (recur (thing->parent t)))
    t))

(defn thing->platform [t]
  {:pre [(thing? t)
         (or (platform? t)
             (namespace? t)
             (def? t))]}
  (if-not (platform? t)
    (when t
      (recur (thing->parent t)))
    t))

(defn thing->namespace [t]
  {:pre [(thing? t)
         (or (namespace? t)
             (def? t))]}
  (if-not (namespace? t)
    (when t
      (recur (thing->parent t)))
    t))

(defn thing->def [t]
  {:pre [(thing? t)
         (def? t)]}
  (if-not (def? t)
    (when t
      (recur (thing->parent t)))
    t))

;; Helpers for stringifying and reading paths
;;--------------------------------------------------------------------
(defn thing->path
  "Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."

  [t]
  {:pre [(thing? t)]}
  (or (::url t)
      (->> t
           (iterate :parent)
           (take-while identity)
           (reverse)
           (map :name)
           (interpose "/")
           (apply str))))

(defn ->Group
  ([groupid]
   {:pre [(string? groupid)]}
   (let [v (->group groupid)]
     (assoc v ::url (thing->path v))))

  ([_ groupid]
   (->Group groupid)))

(defn ->Artifact
  [group artifact]
  (let [v (->artifact group artifact)]
    (assoc v ::url (thing->path v))))

(defn ->Version
  [artifact version]
  (let [v (->version artifact version)]
    (assoc v ::url (thing->path v))))

(defn ->Platform
  [version platform]
  (let [v (->platform version (u/normalize-platform platform))]
    (assoc v ::url (thing->path v))))

(defn ->Ns
  [platform namespace]
  (let [v (->namespace platform namespace)]
    (assoc v ::url (thing->path v))))

(defn ->Def
  [namespace name]
  (let [v (->def namespace name)]
    (assoc v ::url (thing->path v))))

(defn path->thing [path]
  (->> (string/split path #"/")
       (map vector [->Group ->Artifact ->Version ->Platform ->Ns ->Def])
       (reduce (fn [acc [f v]]
                 (if v (f acc v) acc))
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

        (thing? maybe-thing)
        ,,maybe-thing

        :else
        ,,(throw (Exception.
                  (str "Unsupported ensure-thing value "
                       (pr-str maybe-thing))))))
