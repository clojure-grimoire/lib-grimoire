(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking up
  examples, symbols, namespaces and artifacts as values without regard to the
  implementation of the datastore. Ports of Grimoire to different datastores
  should only need to extend the multimethods in this namespace.

  API Contract assumptions:

  - Everything has metadata, even if it's nil. If metadata for a Thing cannot
    be found, then the Thing itself is not in the datastore."
  (:require [grimoire.things :as t]
            [grimoire.util :as util]
            [grimoire.either :as e]
            [guten-tag.core :as v]
            [clojure.core.match :refer [match]]))

(defn dispatch
  "Common dispatch function for all the API multimethods."

  [config & more]
  (v/tag config))

;; Datastore API impl' multimethods - listing & reading
;;--------------------------------------------------------------------
(defmulti -list-groups
  "Implementation extension point of clj::grimoire.api/list-groups. Listing APIs
  must extend this multimethod.

  λ [config] → (Either (Succeed (Seq Group)) (Failure String))"

  dispatch)

(defmulti -list-artifacts
  "Implementation extension point of clj::grimoire.api/list-artifacts. Listing APIs
  must extend this multimethod.

  λ [config Group] → (Either (Succeed (Seq Artifact)) (Failure String))"
  
  dispatch)

(defmulti -list-versions
  "Implementation extension point of clj::grimoire.api/list-versions. Listing APIs
  must extend this multimethod.

  λ [config Artifact] → (Either (Succeed (Seq Version)) (Failure String))"

  dispatch)

(defmulti -list-platforms
  "Implementation extension point of clj::grimoire.api/list-platforms. Listing APIs
  must extend this multimethod.

  λ [config Version] → (Either (Succeed (Seq Platform)) (Failure String))"

  dispatch)

(defmulti -list-namespaces
  "Implementation extension point of clj::grimoire.api/list-namespaces. Listing APIs
  must extend this multimethod.

  λ [config Platform] → (Either (Succeed (Seq Ns)) (Failure String))"

  dispatch)

(defmulti -list-defs
  "Implementation extension point of clj::grimoire.api/list-defs. Listing APIs
  must extend this multimethod.

  λ [config Ns] → (Either (Succeed (Seq Def)) (Failure String))"

  dispatch)

(defmulti -list-notes
  "Implementation extension point of clj::grimoire.api/list-notes. Listing APIs
  must extend this multimethod.

  λ [config Thing] → (Either (Succeed (Seq Note)) (Failure String))"

  dispatch)

(defmulti -list-examples
  "Implementation extension point of clj::grimoire.api/list-examples. Listing APIs
  must extend this multimethod.

  λ [config Thing] → (Either (Succeed (Seq Example)) (Failure String))"

  dispatch)

(defmulti -list-related
  "Implementation extension point of clj::grimoire.api/list-related. Listing APIs
  must extend this multimethod.

  λ [config Thing] → (Either (Succeed (Seq Thing)) (Failure String))"

  dispatch)

(defmulti -read-meta
  "Implementation extension point of clj::grimoire.api/read-meta. Listing APIs
  must extend this multimethod.

  λ [config Thing] → (Either (Succeed Map) (Failure String))"

  dispatch)

(defmulti -read-note
  "Implementation extension point of clj::grimoire.api/read-note. Listing APIs
  must extend this multimethod.

  λ [config Note] → (Either (Succeed String) (Failure String))"

  dispatch)

(defmulti -read-example
  "Implementation extension point of clj::grimoire.api/read-example. Listing APIs
  must extend this multimethod.

  λ [config Artifact] → (Either (Succeed String) (Failure String))"

  dispatch)

(defmulti -thing->prior-versions
  "Implementation extension point of clj::grimoire.api/thing->prior-versions. Listing
  APIs may implement this multimethod, however a default implementation in terms
  of the various listing operations.

  λ [config Thing] → (Either (Succeed (Seq Thing)) (Failure String))"

  dispatch)

(defmulti -search
  "Implementation extension point of clj::grimoire.api/search. Listing APIs may
  implement this multimethod, however a default implementation in terms of the
  various listing operations and clj::clojure.core/for is provided.

  λ [config Pattern] → (Either (Succeed (Seq Thing)) (Failure String))"

  dispatch)

;; Datastore API impl' multimethods - writing
;;--------------------------------------------------------------------
(defmulti -write-meta dispatch)
(defmulti -write-note dispatch)
(defmulti -write-example dispatch)
(defmulti -write-related dispatch)

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------
(defn list-groups
  "Succeeds with a result Seq[Group] representing all Maven groups in the
  queried datastore. Will succeed with an empty result if there are no known
  groups.

  Fails if the datstore isn't correctly configured or missing."

  [config]
  (-list-groups config))

(defn list-artifacts
  "Succeeds with a result Seq[Artifact] representing all Maven artifacts in the
  queried datastore that belong to the specified Group. Will Succeed with an
  empty result if there are no known artifacts.

  Fails if the group is unknown or if another Failure is encountered."
  
  [config group-thing]
  {:pre [(t/group? group-thing)]}
  (-list-artifacts config group-thing))

(defn list-versions
  "Succeeds with a result Seq[Version] representing all Maven versions in the
  queried datastore of the specified Artifact in decending version order. Could
  succeed with an empty result if there are no known versions.

  Fails if the specified Artifact does not exist or if another Failure is
  encountered."

  [config artifact-thing]
  {:pre [(t/artifact? artifact-thing)]}
  (-list-versions config artifact-thing))

(defn list-platforms
  "Succeeds with a result Seq[Platform] representing all Clojure dialects of the
  specified Artifact in the specified datastore. Could succeed with an empty
  result if there is no documentation for any known dialect.

  Fails if the specified Version does not exist or if another Failure is
  encountered."

  [config version-thing]
  {:pre [(t/version? version-thing)]}
  (-list-platforms config version-thing))

(defn list-namespaces
  "Succeeds with a result Seq[Namespace] representing all Clojure namespaces in
  the specified Version. Could succeed with an empty result.

  Fails if the specified Platform does not exist or if another Failure is
  encountered."

  [config platform-thing]
  {:pre [(t/platform? platform-thing)]}
  (-list-namespaces config platform-thing))

(defn list-defs
  "Succeeds with a result Seq[Def] representing all Clojure defs in the
  specified Namespace. Could succeed with an empty result.

  Fails if the specified Namespace does not exist or if another failure is
  encountered."
  
  [config namespace-thing]
  {:pre [(t/namespace? namespace-thing)]}
  (-list-defs config namespace-thing))

(defn list-notes
  "Succeeds with a result Seq[Note] representing all the Notes known on equal or
  prior versions of the given Thing.

  Fails if the specified Thing does not exist, or if a nested Failure is
  encountered."

  [config thing]
  {:pre [(t/thing? thing)]}
  (-list-notes config thing))

(defn read-note
  "Succeeds with a result String being the text of notes read as identified by a
  given notes handle.

  Will Fail if the given Notes Thing does not exist, or if a nested Failure is
  encountered."

  [config note-thing]
  {:pre [(t/note? note-thing)]}
  (-read-note config note-thing))

(defn list-examples
  "Succeeds with a result Seq[Example] encoding for all examples on prior or
  equal versions of the given thing sorted in decending version order.

  Will Fail if the given Def does not exist, or if a nested Failure is
  encountered.

  Note that future versions of this API may extend examples to Namespaces and
  Versions."

  [config def-thing]
  {:pre [(t/def? def-thing)]}
  (-list-examples config def-thing))

(defn read-example
  "Succeeds with a result String being the text of an example read as identified
  by a given examples handle.

  Will Fail if the given Example Thing does not exist, or if a nested Failure is
  encountered.

  Note that future versions of this API may extend examples to Namespaces and
  Versions."

  [config example-thing]
  {:pre [(t/example? example-thing)]}
  (-read-example config example-thing))

(defn read-meta
  "Succeeds returning a Map being the metadata for the specified Thing. No
  backtracking is done to find metadata on prior Versions of the given Thing.

  Fails if the given Thing does not exist.

  Note that per the API contract, failure to find a metadata descriptor for a
  Thing is equivalent to its absence even if other data bout the Thing could be
  found."

  [config thing]
  {:pre [(t/thing? thing)]}
  (-read-meta config thing))

(defn list-related
  "Succeeds with a result Seq[Def] being the sequence of Things \"related\"
  according to the documentation writer to the Thing for which related entities
  was requested.

  Fails if the given Thing does not exist, or if a nested Failure is
  encountered.

  As of 0.6.X, this operation is only defined over Defs, however future versions
  of this API may extend this operation to other types."

  [config thing]
  {:pre [(t/thing? thing)]}
  (-list-related config thing))

;; Interacting with the datastore - writing
;;--------------------------------------------------------------------
(defn write-meta
  "Writes a map, being documentation data, into the datastore as specified by
  config at the def denoted by thing. Note that non-readable structures such as
  Namespaces must be stringified or removed by users. This function provides no
  sanitization.

  Expected keys for symbols:
  - :ns       -> string naming the namespace, namespace itself, or a symbol
  - :name     -> string naming the symbol (unmunged), or a symbol
  - :doc      -> documentation string
  - :arglists -> list of argument vectors
  - :src      -> string of source code
  - :added    -> string being a SemVer version
  - :column   -> integer being colunm number
  - :line     -> integer being line number
  - :file     -> string being file name
  - :redirect -> string being symbol name for implementing macro. only for {:type :sentinel}
  - :type     -> one of #{:macro :fn :var :special :sentinel}

  Expected keys for namespaces:
  - :doc      -> documentation string"

  [config thing data]
  {:pre [(map? data)
         (t/thing? thing)]}
  (-write-meta config thing data))

(defn write-note
  "Writes a string into the datastore specified by the config and the Thing to
  which the resulting Note will be attached. Returns no meaningful value."

  [config thing data]
  {:pre [(t/thing? thing)]}
  (-write-note config thing data))

(defn write-example
  "Writes an example into the datastore specified by the config at the path
  represented by thing. Note that thing need not be a def."

  [config example data]
  {:pre [(t/example? example)]}
  (-write-example config example data))

(defn write-related
  "Writes a sequence of things representing defs into the datastore's related
  file as specified by the target thing."
  
  [config thing related-things]
  {:pre [(every? t/def? related-things)
         (t/def? thing)]}
  (-write-related config thing related-things))

;; Default implementations for operations for which such a thing is sane
;;--------------------------------------------------------------------
(defn thing->prior-versions
  "Succeeds with a result Seq[Thing] representing the argument Thing at earlier
  or equal versions sorted in decending order. Note that this op only supports
  Versions, Namespaces and Defs. Artifacts and Groups do not have versions, and
  will give Failures. The Version component of a Note or an Example is naming,
  and versions of these Things are not guranteed to be interchangable. Trying to
  get a prior version with a

  Fails if a nested Failure is encountered."

  [config thing]
  {:pre [(t/versioned? thing)
         (not (t/leaf? thing))]}
  (-thing->prior-versions config thing))

(declare search)

(def -compare-to-version
  (fn [n]
    (fn [%]
      (let [lhs (util/clojure-version->cmp-key n)
            rhs (-> % t/thing->name util/clojure-version->cmp-key)]
        (<= 0 (compare lhs rhs))))))

(defmethod -thing->prior-versions :default
  [config thing]
  (match thing
    ;; Case of a Version
    ;;
    ;; (list all the versions using the API)
    ;;----------------------------------------
    ([::t/version {:name n
                   :parent {:name aname
                            :parent {:name gname}}}] :seq)
    ,,(search config
              [:version gname aname
               (-compare-to-version n)])

    ([(:or ::t/platform
           ::t/namespace)
      {:name n :parent p}] :seq)
    ,,(let [?versions (thing->prior-versions config p)]
        (if (e/succeed? ?versions)
          (e/succeed
           (for [v (e/result ?versions)]
             (-> thing
                 (assoc :parent v)
                 (dissoc ::t/url))))
          ?versions))

    ;; Case of a Def
    ;;
    ;; (filter out only the older versions)
    ;;----------------------------------------
    ([::t/def {:name n
               :parent
               {:name nsn
                :parent
                {:name pn
                 :parent
                 {:name vn
                  :parent
                  {:name an
                   :parent
                   {:name gn}}}}}}] :seq)
    ,,(search config
              [:def gn an (-compare-to-version vn)
               pn nsn n])))

(defn read-notes
  "Succeeds with a result Seq[Version, string], being the zip of list-notes with
  read-note for each listed note.

  Fails if a nested Failure is encountered.

  Legacy from the 0.7.X and earlier API. Note that this function does _not_
  return the Note instances themselves, only the versions each read note is
  attached to."

  [config thing]
  (let [?notes (list-notes config thing)]
    (if (e/succeed? ?notes)
      (try
        (e/succeed
         (for [note (e/result ?notes)]
           [(t/thing->version note)
            (e/result (read-note config note))]))
        (catch Exception e
          (e/fail (.getMessage e))))
      ?notes)))

(defn search
  "Succeeds with a result Seq[Thing], being a list of all the Things in the
  given datastore which match the given search structure.

  Search patterns are vectors of the form [<type> &parts] where a type is one of
  the Thing keywords, and &parts is a sequence terms being either sets of
  strings, strings, regexes, functions, nil or the keyword :any.

  - nil as a term matches any Thing
  - :any as a term matches any Thing
  - Sets as a term match Thing with its Name in the set
  - Strings as a term match any Thing with an equal Name
  - Regexps as a Term match any Thing with a matching Name
  - Fns as a term match any Thing such that (f T) is truthy

  Examples:
  > [:def :any :any :any :any \"clojure.core\" \"concat\"]

  Will match all instances of Defs named \"clojure.core/concat\" on all
  platforms in all versions in all artifacts in all groups.

  > [:ns :any \"clojure\" :any :any \"clojure.set\"]

  Will match all instances of Nss named \"clojure.set\" in all artifacts named
  \"clojure\" in all versions, platforms and groups.

  > [:ns #\"org.*\" :any :any :any :any]

  Will match all instances of Ns in any platform of any version of any artifact
  in a Maven group matching the regex #\"org.*\""

  [config pattern]
  (-search config pattern))

(defn- matches?
  "Helper function to clj::grimoire.api/search which serves to determine whether
  a pattern matches a Thing.

  Supported patterns:
  - nil, matches anything
  - :any, matches anything
  - Sets, match any member by Name
  - Strings, matches any Thing with an equal Name
  - Regexp, matches any Thing with a matching Name
  - Fn, matches iff (f T) is truthy"

  [pattern term]
  {:pre [(t/thing? term)]}
  (cond
    (or (= nil pattern)
        (= :any pattern))
    ,,true

    (set? pattern)
    ,,(contains? pattern (t/thing->name term))

    (string? pattern)
    ,,(= pattern (t/thing->name term))

    (instance? java.util.regex.Pattern pattern)
    ,,(re-find pattern (t/thing->name term))

    (fn? pattern)
    ,,(pattern term)))

(defn- downgrade
  "Helper to `search`. Returns the left-recursive subquery from a given
  query. Used to implement clj::grimoire.api/search in terms of left recursive
  query descent."

  [pattern]
  (let [tm {:def      :ns
            :ns       :platform
            :platform :version
            :version  :artifact
            :artifact :group}]
    (vec (cons (tm (first pattern))
                (rest (butlast pattern))))))

(defn- forge
  [pattern]
  (match (first pattern)
    :def      t/->Def
    :ns       t/->Ns
    :platform t/->Platform
    :version  t/->Version
    :artifact t/->Artifact
    :group    #(t/->Group %2)))

(defn- recur-and-filter [cfg pattern el list-fn]
  (let [?subterms (-search cfg (downgrade pattern))]
    (if (e/succeed? ?subterms)
      (let [results (e/result ?subterms)]
        (->> (cond (set? el)
                   ,,(let [ctor (forge pattern)]
                       (for [r results
                             e el]
                         (ctor r e)))
                   
                   (string? el)
                   ,,(let [ctor (forge pattern)]
                       (map #(ctor % el) results))

                   :else
                   ,,(for [r     results
                           ir    (e/result (list-fn cfg r))
                           :when (matches? el ir)]
                       ir))
             (filter #(e/succeed? (read-meta cfg %)))
             (e/succeed)))
      ?subterms)))

;; Default naive implementation of searching as above
(defmethod -search :default [config pattern]
  (let [f (partial recur-and-filter config pattern)]
    (match pattern
      ;; Case of a def
      ;;----------------------------------------
      [:def gid art v plat ns name]
      ,,(f name list-defs)

      ;; Case of a ns
      ;;----------------------------------------
      [:ns gid art v plat ns]
      ,,(f ns list-namespaces)

      ;; Case of a platform
      ;;----------------------------------------
      [:platform gid art v plat]
      ,,(f plat list-platforms)
      
      ;; Case of a version
      ;;----------------------------------------
      [:version gid art v]
      ,,(f v list-versions)
      
      ;; Case of an artifact
      ;;----------------------------------------
      [:artifact gid art]
      ,,(f art list-artifacts)

      ;; Case of a group
      ;;----------------------------------------
      [:group gid]
      ,,(e/succeed
         (for [cg    (e/result (list-groups config))
               :when (matches? gid cg)]
           cg)))))

(defn resolve-short-string
  "Succeeds with a result Thing, mapping a short string as generated by
  clj::grimoire.things/thing->short-string back to a thing via
  clj::grimoire.api/search."

  [config s]
  {:pre [(string? s)]}
  (if-let [res (t/parse-short-string s)]
    (match [res]
      ;; Case of a def
      ;;----------------------------------------
      [([:def gid art v plat ns name] :seq)]
      (let [?res (search config res)]
        (if (e/succeed? ?res)
          (e/succeed (first (e/result ?res)))
          ?res))
      
      ;; Case of a ns
      ;;----------------------------------------
      [([:ns  gid art v plat ns] :seq)]
      (let [?res (search config res)]
        (if (e/succeed? ?res)
          (e/succeed (first (e/result ?res)))
          ?res))

      ;; Default case
      ;;----------------------------------------
      [_]
      (e/fail "Unknown parse-short-string result!"))
    (e/fail "Could not parse string!")))
