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
            [detritus.variants :as v]
            [version-clj.core :as semver]))

(defn dispatch
  "Common dispatch function for all the API multimethods"
  [config & more]
  (v/tag config))

;; Datastore API impl' multimethods - listing & reading
;;--------------------------------------------------------------------
(defmulti -list-groups dispatch)
(defmulti -list-artifacts dispatch)
(defmulti -list-versions dispatch)
(defmulti -list-platforms dispatch)
(defmulti -list-namespaces dispatch)
(defmulti -list-defs dispatch)
(defmulti -list-notes dispatch)
(defmulti -list-examples dispatch)
(defmulti -list-related dispatch)

(defmulti -read-meta dispatch)
(defmulti -read-note dispatch)
(defmulti -read-example dispatch)

;; Datastore API impl' multimethods - listing & reading
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
  "Succeeds with a result Seq[Tuple[version, example-text]] for all examples on
  prior or equal versions of the given thing sorted in decending version order.

  Fails if the given Def does not exist, or if a nested Failure is encountered.

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
         (not (:handle thing))]}
  (let [thing    (t/ensure-thing thing)
        currentv (t/thing->version thing)               ; version handle
        current  (-> currentv :name util/normalize-version)
        added    (-> (read-meta config thing)
                     e/result
                     (get :added "0.0.0")
                     util/normalize-version)
        unv-path (t/thing->relative-path t/version thing)
        versions (e/result (list-versions config (:parent currentv)))]
    (e/succeed
     (for [v     versions
           :when (<= 0 (semver/version-compare (:name v) added))
           :when (>= 0 (semver/version-compare (:name v) current))]
       ;; FIXME: this could be a direct constructor given an
       ;; appropriate vehicle for doing so since the type is directed
       ;; and single but may not generally make sense if this is not
       ;; the case.
       (t/path->thing (str (t/thing->path v) "/" unv-path))))))

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
