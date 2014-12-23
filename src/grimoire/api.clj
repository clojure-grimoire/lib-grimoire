(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking
  up examples, symbols, namespaces and artifacts as values without
  regard to the implementation of the datastore. Ports of Grimoire to
  different datastores should only need to extend the multimethods in
  this namespace.")

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

(defmulti list-namespaces
  "Succeeds with a result Seq[Namespace] representing all Clojure namespaces in
  the specified Version. Could succeed with an empty result. Fails if the
  specified Version does not exist or if another Failure is encountered."

  {:arglists '[[config version-thing]]}
  dispatch)

(defmulti list-classes
  "Succeeds with a result Seq[Class] representing all Java classes in the
  specified Version. Could succeed with an empty result. Fails if the specified
  Version does not exist or of another failure is encountered."
  
  {:arglists '[[config version-thing]]}
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

(defmulti read-examples
  "Succeeds with a result Seq[Tuple[version, example-text]] for all examples on
  prior or equal versions of the given thing sorted in decending version
  order. Will Fail if the given Def does not exist, or if a nested Failure is
  encountered."

  {:arglists '[[config def-thing]]}
  dispatch)

(defmulti read-meta

  {:arglists '[[config thing]]}
  dispatch)

(defmulti read-related
  "Returns a sequence of things representing symbols related to this or prior
  versions of the given symbol."

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
