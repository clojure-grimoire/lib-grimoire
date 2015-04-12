(ns grimoire.things
  "This namespace implements a \"thing\" structure, approximating a URI, for
  uniquely naming and referencing entities in a Grimoire documentation
  store.

  Thing     ::= Sum[Group, Artifact, Version, Platform,
                    Namespace, Def, Note, Example];
  Group     ::= Record[                   Name: String];
  Artifact  ::= Record[Parent: Group,     Name: String];
  Version   ::= Record[Parent: Artifact,  Name: String];
  Platform  ::= Record[Parent: Version,   Name: String];
  Namespace ::= Record[Parent: Platform,  Name: String];
  Def       ::= Record[Parent: Namespace, Name: String];

  Note      ::= Record[Parent: Thing,     Handle: String];
  Example   ::= Record[Parent: Thing,     Handle: String];"
  (:refer-clojure :exclude [def namespace])
  (:require [clojure.string :as string]
            [clojure.core.match :refer [match]]
            [grimoire.util :as u]
            [guten-tag.core :as t]
            [cemerick.url :as url]))

(t/deftag group
  "Represents a Maven group."
  [name]
  {:pre [(string? name)]})

(t/deftag artifact
  "Represents a Maven artifact, rooted on a group."
  [parent, name]
  {:pre [(group? parent)
         (string? name)]})

(t/deftag version
  "Represents a Maven version, rooted on an artifact."
  [parent, name]
  {:pre [(artifact? parent)
         (string? name)]})

(t/deftag platform
  "Represents a Clojure \"platform\" rooted on a version of an
  artifact.

  Platforms are a construct and represent a versioned set of
  namespaces (and thus of defs) defining the versioned package at that
  version. The idea is that a single artifact may have \"platform\"
  code for any of Clojure, ClojureScript, ClojureCLR and soforth
  simultaneously. Selecting a platform in a tree thus selects a set of
  namespaces and defs which are particular to this platform. It also
  allows Grimoire to host what would otherwise be name-colliding
  functions which are really implicitly differentiated by platform."
  [parent, name]
  {:pre [(version? parent)
         (string? name)]})

(t/deftag namespace
  "Represents a Clojure \"namespace\" rooted on a platform in a
  version of an artifact."
  [parent, name]
  {:pre [(platform? parent)
         (string? name)]})

(t/deftag def
  "Represents a Clojure \"Def\" rooted in a namespace on a platform in
  a version of an artifact."
  [parent, name]
  {:pre [(namespace? parent)
         (string? name)]})

(declare thing?)

(t/deftag note
  "Represents a single block of notes on an arbitrary Thing as
  identified by a Handle. The Handle is intended to be some structure
  such as a file path, record ID, UUID or something else uniquely
  naming a specific note."
  [parent, name, handle]
  {:pre [(thing? parent)
         (string? name)
         (string? handle)]})

(t/deftag example
  "Represents a single example on an arbitrary Thing as identified by
  a Handle. The Handle is intended to be some structure such as a file
  path, record ID, UUID or other unique identifier for that singular
  specific example."
  [parent, name, handle]
  {:pre [(thing? parent)
         (string? name)
         (string? handle)]})

;; Helpers for walking thing paths


(defn leaf?
  "Predicate testing whether the input Thing is either an example or a
  note."
  [t]
  (or (note? t)
      (example? t)))

(defn namespaced?
  "Predicate testing whether the input either is a namespace or has a namespace
  as a parent."
  [t]
  (or (namespace? t)
      (def? t)
      (and (leaf? t)
           (namespaced? (:parent t)))))

(defn platformed?
  "Predicate testing whether the input either is a platform or has a platform as
  a parent."
  [t]
  (or (namespaced? t)
      (platform? t)
      (and (leaf? t)
           (platformed? (:parent t)))))

(defn versioned?
  "Predicate testing whether the input exists within the subset of the \"thing\"
  variant which can be said to be \"versioned\" in that it is rooted on a
  Version instance and thus a version instance can be reached by upwards
  traversal."
  [t]
  (or (platformed? t)
      (version? t)
      (and (leaf? t)
           (versioned? (:parent t)))))

(defn artifacted?
  "Predicate testing whether the input either is an artifact or has an artifact
  as a parent."
  [t]
  (or (versioned? t)
      (artifact? t)
      (and (leaf? t)
           (artifacted? (:parent t)))))

(defn grouped?
  "Predicate testing whether the input either is a group or has a group as a
  parent."
  [t]
  (or (artifacted? t)
      (group? t)
      (and (leaf? t)
           (grouped? (:parent t)))))

(defn thing?
  "Predicate testing whether the input exists within the \"thing\" variant of
  Σ[Group, Artifact,Version, Platform, Namespace, Def]"
  [t]
  (grouped? t))

(defn thing->parent
  "Function from any object to Maybe[Thing]. If the input is a thing, returns
  the parent (maybe nil) of that Thing. Otherwise returns nil."
  [t]
  (when (thing? t)
    (:parent t)))

(defn thing->name
  "Function from an object to Maybe[String]. If the input is a thing, returns
  the name of the Thing. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (:name t))

;; smarter url caching constructors


(declare thing->url-path)

(defn ->Group
  ([groupid]
   {:pre [(string? groupid)]}
   (let [v (->group groupid)]
     (assoc v ::url (thing->url-path v))))

  ([_ groupid]
   (->Group groupid)))

(defn ->Artifact
  [group artifact]
  (let [v (->artifact group artifact)]
    (assoc v ::url (thing->url-path v))))

(defn ->Version
  [artifact version]
  (let [v (->version artifact version)]
    (assoc v ::url (thing->url-path v))))

(defn ->Platform
  [version platform]
  (let [v (->platform version (u/normalize-platform platform))]
    (assoc v ::url (thing->url-path v))))

(defn ->Ns
  [platform namespace]
  (let [v (->namespace platform namespace)]
    (assoc v ::url (thing->url-path v))))

(defn ->Def
  [namespace name]
  (let [v (->def namespace name)]
    (assoc v ::url (thing->url-path v))))

(defn ->Example
  [thing name handle]
  (let [v (->example thing name handle)]
    (assoc v ::url handle)))

(defn ->Note
  [thing name handle]
  (let [v (->note thing name handle)]
    (assoc v ::url handle)))

;; Manipulating things and strings

(defn thing->path
  "Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."
  [t]
  {:pre [(thing? t)]}
  (or (::url t)
      (thing->url-path t)))

(defn path->thing
  "String to Thing transformer which builds a Thing tree by splitting on /. The
  resulting things are rooted on a Group as required by the definition of a
  Thing."
  [path]
  (->> (string/split path #"/" 6)
       (map vector [->Group ->Artifact ->Version ->Platform ->Ns ->Def])
       (reduce (fn [acc [f v]]
                 (if v (f acc v) acc))
               nil)))

(defn thing->relative-path
  "Function from a Thing type and a Thing instance which walks the instance's
  parent tree until it reaches an instance of the given Thing type. Returns a
  string representing the relative path of the given Thing instance with respect
  to the parent Thing type."
  [t thing]
  {:pre [(thing? thing)
         (t/TagDescriptor? t)]}
  (->> thing
       (iterate thing->parent)
       (take-while identity)
       (take-while #(not= (t/tag %1) (:tag t)))
       (reverse)
       (map thing->name)
       (interpose "/")
       (apply str)))

(defn thing->root-to
  "Complement of thing->relative-path. Given a Thing instance and a Thing type,
  returns the subpath of the given Thing instance from the root (Group) to the
  given Thing type."
  [t thing]
  {:pre [(thing? thing)
         (t/TagDescriptor? t)]}
  (->> thing
       (iterate thing->parent)
       (take-while identity)
       (drop-while #(not= (t/tag %1) (:tag t)))
       (reverse)
       (map thing->name)
       (interpose "/")
       (apply str)))

(defn ensure-thing
  "Transformer which, if given a string, will construct a Thing (with a warning)
  and if given a Thing will return the Thing without modification. Intended as a
  guard for potentially mixed input situations."
  [maybe-thing]
  (cond (string? maybe-thing)
        ,,(do (.write *err* "Warning: building a thing from a string via ensure-string!\n")
              (path->thing maybe-thing))

        (thing? maybe-thing)
        ,,maybe-thing

        :else
        ,,(throw
           (Exception.
            (str "Unsupported ensure-thing value "
                 (pr-str maybe-thing))))))

;; Traversing things

(defn thing->group
  "Function from a Thing to a Group. If the Thing is rooted on a Group,
  or is a Group, traverses thing->parent until a Group is produced. Otherwise
  returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (grouped? t)
    (if-not (group? t)
      (when t
        (recur (thing->parent t)))
      t)))

(defn thing->artifact
  "Function from a Thing to an Artifact. If the Thing is rooted on an Artifact,
  or is an Artifact, traverses thing->parent until the rooting Artifact is
  reached and then returns that value. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (artifacted? t)
    (if-not (artifact? t)
      (when t
        (recur (thing->parent t)))
      t)))

(defn thing->version
  "Function from a Thing to a Verison. If the Thing is rooted on a Version or is
  a Version, traverses thing->parent until the rooting Version is reached and
  then returns that value. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (versioned? t)
    (if-not (version? t)
      (when t
        (recur (thing->parent t)))
      t)))

(defn thing->platform
  "Function from a Thing to a Platform. If the Thing is rooted on a Platform or
  is a Platform traverses thing->parent until the rooting Platform is reached
  and then returns that value. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (platformed? t)
    (if-not (platform? t)
      (when t
        (recur (thing->parent t)))
      t)))

(defn thing->namespace
  "Function from a Thing to a Namespace. If the Thing is rooted on a Platform or
  is a Platform traverses thing->parent until the rooting Platform is reached
  and then returns that value. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (namespaced? t)
    (if-not (namespace? t)
      (when t
        (recur (thing->parent t)))
      t)))

(defn thing->def
  "Function from a Thing to a Def. If the Thing either is a Def or is rooted on
  a Def, traverses thing->parent until the rooting Def is reached and then
  returns that value. Otherwise returns nil."
  [t]
  {:pre [(thing? t)]}
  (when (def? t) t))

;; Bits and bats

(defn thing->url-path
  "Function from a Thing to a munged and URL safe Thing path"
  [t]
  {:pre [(thing? t)]}
  (match [t]
    [([::def   {:name n :parent p}] :seq)]
    ,,(str (thing->url-path p) "/" (u/munge n))

    [([::group {:name n}] :seq)]
    ,,n

    [([_       {:name n :parent p}] :seq)]
    ,,(str (thing->url-path p) "/" n)))

;; FIXME: this function could probably be a little more principled,
;; but so be it.
(defn url-path->thing
  "Function from a URL to a Thing. Complement of thing->url-path."
  [url]
  (let [path-elems (string/split url #"/")
        path-elems (if (<= 6 (count path-elems))
                     (concat
                      (take 5 path-elems)
                      [(url/url-decode (nth path-elems 5))]
                      (drop 6 path-elems))
                     path-elems)]
    (path->thing (string/join "/" path-elems))))

(defn thing->type-name
  [t]
  {:pre [(thing? t)]}
  (match [t]
    [([::group     {}] :seq)] "group"
    [([::artifact  {}] :seq)] "artifact"
    [([::version   {}] :seq)] "version"
    [([::platform  {}] :seq)] "platform"
    [([::namespace {}] :seq)] "namespace"
    [([::def       {}] :seq)] "def"))

(defn thing->full-uri
  "Function from a Thing to a String representing a unique Thing naming URI.

  URIs have the same structure as thing->url-path but are prefixed by <t>:
  where <t> is the lower cased name of the type of the input Thing.

  For example, a Thing represeting org.clojure/clojure would give the full URI
  grim+artifact:org.clojure/clojure. A Thing representing org.clojure/clojure/1.6.0
  would likewise be grim+version:org.clojure/clojure/1.6.0 and soforth."
  [t]
  {:pre [(thing? t)]}
  (format "grim+%s:%s"
          (thing->type-name t)
          (thing->url-path t)))

(def full-uri-pattern
  #"(grim\+(group|artifact|version|platform|namespace|def)(\+(note|example))?):([^?&#]+)([?&#].*)?")

(defn full-uri->thing
  "Complement of thing->full-uri."
  [uri-string]
  {:pre [(string? uri-string)]}
  (let [[_ scheme type _ extension path
         :as groups] (re-find full-uri-pattern uri-string)]
    (assert groups "Failed to parse URI. No regex match!")
    (println groups)
    (let [t (url-path->thing path)]
      (assert (= (thing->type-name t) type)
              "Failed to parse URI. Path didn't round trip to expected type!")
      t)))

(def short-string-pattern
  #"(\w{3,6})::([^\s/:]+)(/([^:\s]+))?")

(defn thing->short-string
  "Function from a Thing to a String representing a mostly unique naming string.
  
  Unlike thing->full-uri, thing->short-string will discard exact artifact, group
  and verison information instead giving only a URI with respect to the
  platform, namespace and name of a Thing.

  For example, the Thing representing
  org.clojure/clojure/1.6.0/clj/clojure.core/+ would give the short string
  clj::clojure.core/+."
  [t]
  {:pre  [(platformed? t)]
   :post [(re-find short-string-pattern %)]}
  (match [t]
    [([::namespace {:name nn
                    :parent {:name pn}}]
      :seq)]
    ,,(format "%s::%s" pn nn)

    [([::def {:name n
              :parent {:name nn
                       :parent {:name pn}}}]
      :seq)]
    ,,(format "%s::%s/%s"
              pn nn n)))

;; short-string->thing to be defined in terms of a search for the latest version.
(defn parse-short-string
  "Function from a String as generated by thing->short-string to one of
  either [:def nil nil nil <ns-name> <def-name>] or [:ns nil nil nil
  <ns-name>]. The intention is that this function can be used to parse
  short-strings into structures for which Things can be looked up out of a
  datastore. Returns nil on failure to parse."
  [s]
  {:pre [(string? s)]}
  (let [[_s platform ns _ ?def :as match] (re-find short-string-pattern s)]
    (when match
      (if ?def
        [:def nil nil nil platform ns ?def]
        [:ns  nil nil nil platform ns]))))
