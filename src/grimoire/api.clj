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
  "Returns a sequence of Thing[:group] representing all Maven groups
  in the queried datastore."

  {:arglists '[[config]]}
  dispatch)

(defmulti list-artifacts
  "Returns a sequence of Thing[:artifact] representing all Maven
  artifacts in the queried datastore that belong to the specified
  Thing[:group]."
  
  {:arglists '[[config group-thing]]}
  dispatch)

(defmulti list-versions

  {:arglists '[[config artifact-thing]]}
  dispatch)

(defmulti list-namespaces

  {:arglists '[[config version-thing]]}
  dispatch)

(defmulti list-classes
  
  {:arglists '[[config version-thing]]}
  dispatch)

(defmulti list-defs

  {:arglists '[[config namespace-thing]]}
  dispatch)

(defmulti thing->prior-versions
  "Returns a sequence of things representing itself at earlier or equal versions."

  dispatch)

(defmulti read-notes
  "Returns a sequence of pairs [version note-text] for all notes on
  prior or equal versions of the given thing."

  {:arglists '[[config thing]]}
  dispatch)

(defmulti read-examples
  "Returns a sequence of pairs [version example-text] for all examples on prior
  or equal versions of the given thing."

  {:arglists '[[config thing]]}
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
