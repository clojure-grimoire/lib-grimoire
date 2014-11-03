(ns grimoire.things
  (:refer-clojure :exclude [isa?])
  (:require [clojure.string :as string]))

(defn isa? [t o]
  (= (:type o) t))

(defn ->T [t parent name]
  {:type   t
   :parent parent
   :name   name
   :uri    (str (:uri parent)
                (when (:uri parent) "/")
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
  (or (:uri thing)
      (->> thing
           (iterate :parent)
           (take-while identity)
           (reverse)
           (map :name)
           (interpose "/")
           (apply str))))

(defn ->Def [groupid artifactid version namespace name]
  (->> (map vector
            [:group :artifact :version :namespace :def]
            [groupid artifactid version namespace name])
       (reduce (fn [parent [t el]]
                 (->T t parent el))
               nil)))

(defn path->thing [path]
  (->> (string/split path #"/")
       (apply ->Def)))

;; Manipulating things
;;--------------------------------------------------------------------
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
        ,,(path->thing maybe-thing)

        (map? maybe-thing)
        ,,maybe-thing

        :else
        ,,(throw (Exception.
                  (str "Unsupported ensure-thing value "
                       (pr-str maybe-thing))))))
