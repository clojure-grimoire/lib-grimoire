(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking
  up examples, symbols, namespaces and artifacts as values without
  regard to the implementation of the datastore. Ports of Grimoire to
  different datastores should only need to extend the multimethods in
  this namespace.

  API Contract assumptions:
  
  - Everything has metadata, even if it's nil. If metadata for a Thing
    cannot be found, then the Thing itself is not in the datastore."
  (:require [version-clj.core :as semver]
            [grimoire.things :as t]
            [grimoire.util :as util]
            [grimoire.either :as e]))

(defn dispatch [config & more]
  (-> config :datastore :mode))

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------

(defmulti list-groups
  "Succeeds with a result Seq[Group] representing all Maven groups in the
  queried datastore. Will succeed with an empty result if there are no known
  groups. Fails if the datstore isn't correctly configured or missing."

  {:arglists '[[config]]}
  dispatch)

(defmulti list-artifacts
  "Succeeds with a result Seq[Artifact] representing all Maven artifacts in the
  queried datastore that belong to the specified Group. Will Succeed with an
  empty result if there are no known artifacts. Fails if the group is unknown or
  if another Failure is encountered."
  
  {:arglists '[[config group-thing]]}
  dispatch)

(defmulti list-versions
  "Succeeds with a result Seq[Version] representing all Maven versions in the
  queried datastore of the specified Artifact. Could succeed with an empty
  result if there are no known versions. Fails if the specified Artifact does
  not exist or if another Failure is encountered."

  {:arglists '[[config artifact-thing]]}
  dispatch)

(defmulti list-platforms
  "Succeeds with a result Seq[Platform] representing all Clojure dialects of the
  specified Artifact in the specified datastore. Could succeed with an empty
  result if there is no documentation for any known dialect. Fails if the
  specified Version does not exist or if another Failure is encountered."

  {:arglists '[[config version-thing]]}
  dispatch)

(defmulti list-namespaces
  "Succeeds with a result Seq[Namespace] representing all Clojure namespaces in
  the specified Version. Could succeed with an empty result. Fails if the
  specified Platform does not exist or if another Failure is encountered."

  {:arglists '[[config platform-thing]]}
  dispatch)

(defmulti list-classes
  "Succeeds with a result Seq[Class] representing all Java classes in the
  specified Version. Could succeed with an empty result. Fails if the specified
  Version does not exist or of another failure is encountered.

  Deprecated as of 0.7.0
  Will be removed in 0.8.0"
  
  {:arglists   '[[config version-thing]]
   :deprecated "0.7.0"}
  dispatch)

(defmulti list-defs
  "Succeeds with a result Seq[Def] representing all Clojure defs in the
  specified Namespace. Could succeed with an empty result. Fails if the
  specified Namespace does not exist or if another failure is encountered."
  
  {:arglists '[[config namespace-thing]]}
  dispatch)

(defmulti thing->prior-versions
  "Succeeds with a result Seq[Thing] representing the argument Thing at earlier
  or equal versions sorted in decending order. Note that this op only supports
  Versions, Namespaces and Defs. Artifacts and Groups do not have versions, and
  will give Failures. Will Fail if a nested Failure is encountered."

  {:arglists '[[config thing]]}
  dispatch)

(defmulti read-notes
  "Succeeds with a result Seq[Tuple[Version, String]] being all notes on prior
  or equal versions of the given thing sorted in decending version order. Will
  Fail if the given Thing does not exist, or if a nested Failure is
  encountered."

  {:arglists '[[config thing]]}
  dispatch)

;; FIXME: Examples on Namespaces? Versions?
(defmulti read-examples
  "Succeeds with a result Seq[Tuple[version, example-text]] for all examples on
  prior or equal versions of the given thing sorted in decending version
  order. Will Fail if the given Def does not exist, or if a nested Failure is
  encountered.

  Note that future versions of this API may extend examples to Namespaces and
  Versions."

  {:arglists '[[config def-thing]]}
  dispatch)

(defmulti read-meta
  "Succeeds returning a Map being the metadata for the specified Thing. No
  backtracking is done to find metadata on prior Versions of the given
  Thing. Fails if the given Thing does not exist.

  Note that per the API contract, failure to find a metadata descriptor for a
  Thing is equivalent to its absence even if other data bout the Thing could be
  found."

  {:arglists '[[config thing]]}
  dispatch)

(defmulti read-related
  "Succeeds with a result Seq[Def] being the sequence of Things \"related\"
  according to the documentation writer to the Thing for which related entities
  was requested.

  As of 0.6.X, this operation is only defined over Defs, however future versions
  of this API may extend this operation to other types."

  {:arglists '[[config thing]]}
  dispatch)

;; Interacting with the datastore - writing
;;--------------------------------------------------------------------

(defmulti write-meta
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

  {:arglists '[[config thing data]]}
  dispatch)

(defmulti write-notes
  "Writes a string into the datastore specified by the config at the path
  represented by thing. Note that thing need not be a def."

  {:arglists '[[config thing data]]}
  dispatch)

(defmulti write-example
  "Writes an example into the datastore specified by the config at the
  path represented by thing. Note that thing need not be a def."

  {:arglists '[[config thing data]]}
  dispatch)

(defmulti write-related
  "Writes a sequence of things representing defs into the datastore's
  related file as specified by the target thing."
  
  {:arglists '[[config thing related-things]]}
  dispatch)

;; Default implementations for operations for which such a thing is sane
;;--------------------------------------------------------------------

(defmethod thing->prior-versions :default [config thing]
  ;; FIXME: this is entirely common to fs/read's thing->versions
  {:pre [(#{:version :platform :namespace :def} (:type thing))]}
  (let [thing    (t/ensure-thing thing)
        currentv (t/thing->version thing)               ; version handle
        current  (-> currentv :name util/normalize-version)
        added    (-> (read-meta config thing)
                     e/result
                     (get :added "0.0.0")
                     util/normalize-version)
        unv-path (t/thing->relative-path :version thing)
        versions (e/result (list-versions config (:parent currentv)))]
    (-> (for [v     versions
              :when (<= 0 (semver/version-compare (:name v) added))
              :when (>= 0 (semver/version-compare (:name v) current))]
          ;; FIXME: this could be a direct constructor given an
          ;; appropriate vehicle for doing so since the type is directed
          ;; and single but may not generally make sense if this is not
          ;; the case.
          (t/path->thing (str (t/thing->path v) "/" unv-path)))
        e/succeed)))
