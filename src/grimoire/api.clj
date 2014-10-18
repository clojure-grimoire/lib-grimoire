(ns grimoire.api
  "This namespace serves to implement an abstraction layer for looking
  up examples, symbols, namespaces and artifacts as values without
  regard to the implementation of the datastore. Ports of Grimoire to
  different datastores should only need to tweak this namespace."

  (:require [grimoire.web.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-semver.core :as semver]))

(defmacro maybe-file [& forms]
  `(let [f (apply io/file forms)]
     (when (.exists f) f)))

(def type-file  ":type.txt")
(def docs-file  ":docs.txt")
(def notes-file ":notes.txt")
(def url-file   ":url.txt")
(def added-file ":added.txt")

;; Group stuff
;;--------------------------------------------------------------------
(defrecord GroupHandle [handle name])

(defn list-groups
  "λ [config] → (Seq GroupHandle)

  Provides an interface for enumeriating all known Maven groups as group handle
  objects which can be used to enumerate artifacts or for documentation. Returns
  an unordered sequence of GroupHandles."

  [{{docs :docs} :datastore}]
  {:pre [(or (instance? java.io.File docs)
             (instance? java.lang.String docs))]}
  (let [f (io/file docs)]
    (when (and (.exists f)
               (.isDirectory f))
      (for [f' (.listFiles f)
            :when (.isDirectory f')
            :when (= "group" (slurp (io/file f' type-file)))]
        (->GroupHandle f' (. f' (getName)))))))

;; Artifact stuff
;;--------------------------------------------------------------------
(defrecord ArtifactHandle [handle parent name])

(defn list-artifacts
  "λ [config ← Map, group ← GroupHandle] → (Seq ArtifactHandle)

  Provides an interface for enumerating all the known artifacts in a given
  Group. Returns an unordered sequence of ArtifactHandles."

  [config group]
  {:pre [(instance? GroupHandle group)]}
  (when-let [f (:handle group)]
    (for [f'    (.listFiles f)
          :when (.isDirectory f')
          :when (= "artifact" (slurp (io/file f' type-file)))]
      (->ArtifactHandle f' group (. f' (getName))))))

;; Version stuff
;;--------------------------------------------------------------------
(defrecord VersionHandle [handle parent name])

(defn list-versions
  "λ [config ← Map, artifact ← ArtifactHandle] → (Seq VersionHandle)

  Provides an interface for enumerating all the known versions if a given
  Artifact. Returns a sequence of VersionHandles."

  [config artifact]
  {:pre [(instance? ArtifactHandle artifact)]}
  (when-let [f (:handle artifact)]
    (for [f'    (.listFiles f)
          :when (.isDirectory f')]
      (->VersionHandle f' artifact (. f' (getName))))))

(defn- list-type-filtered [type ctor config parent]
  (when-let [f (:handle parent)]
    (for [f'    (.listFiles f)
          :when (.isDirectory f')
          :let  [typef (io/file f' type-file)]
          :when (.exists typef)
          :when (= type (slurp typef))]
      (ctor f' parent (. f' (getName))))))

;; Namespace stuff
;;--------------------------------------------------------------------
(defrecord NamespaceHandle [handle parent name])

(defn list-namespaces
  "λ [config ← Map, version ← VersionHandle] → (Seq NamespaceHandle)

  Provides a mechanism for enumerating all the known namespaces in a
  given Version. Returns an unordered sequence of
  VersionHandles."

  [config version]
  {:pre [(instance? VersionHandle version)]}
  (list-type-filtered "ns" ->NamespaceHandle config version))

;; Package stuff
;;--------------------------------------------------------------------
(defrecord PackageHandle [handle parent name])

(defn list-packages
  "λ [config ← Map, version ← VersionHandle] → (Seq PackageHandle)

  Provides a mechanism for enumerating all of the Java packages in a given
  Version entity. Returns an unordered sequence of PackageHandles."

  [config version]
  {:pre [(instance? VersionHandle version)]}
  (list-type-filtered "package" ->PackageHandle config version))

;; Def stuff
;;--------------------------------------------------------------------
;; Type one of #{:symbol :var :fn :macro :class}
(defrecord DefHandle [handle parent name type])

(defn- list-ns-contents [ctor type config parent]
  (when-let [f (:handle parent)]
    (for [f'    (.listFiles f)
          :when (.isDirectory f')
          :let  [typef (io/file f' type-file)]
          :when (.exists typef)
          :when (= type (slurp typef))]
      (ctor f' parent (. f' (getName)) type))))

(defn list-vars
  "λ [config ← Map, namespace ← NamespaceHandle] → (Seq DefHandle)

  Provides a mechanism for enumerating all the vars (dynamic variables) packaged
  within a given Namespace. Returns an unordered sequence of DefHandles."

  [config namespace]
  {:pre [(instance? NamespaceHandle namespace)]}
  (list-ns-contents ->DefHandle "var" config namespace))

(defn list-fns
  "λ [config ← Map, namespace ← NamespaceHandle] → (Seq DefHandle)

  Provides a mechanism for enumerating all of the functions (fns) def'd within
  the given namespace. Returns a sequence of unordered DefHandles."

  [config namespace]
  {:pre [(instance? NamespaceHandle namespace)]}
  (list-ns-contents ->DefHandle "fn" config namespace))

(defn list-macros
  "λ [config ← Map, namespace ← NamespaceHandle] → (Seq DefHandle)

  Provides a mechanism for enumerating all of the macros def'd within the given
  namespace. Returns an unordered sequence of DefHandles."

  [config namespace]
  {:pre [(instance? NamespaceHandle namespace)]}
  (list-ns-contents ->DefHandle "macro" config namespace))

(defn list-symbols
  "λ [config ← Map, namespace ← NamespaceHandle] → (Seq DefHandle)

  Provides a mechanism for enumerating all the symbols (non-def'd symbols with
  special meanings). Returns an unordered sequence of DefHandles representing
  these symbols."

  [config namespace]
  {:pre [(instance? NamespaceHandle namespace)]}
  (list-ns-contents ->DefHandle "symbol" config namespace))

;; Class stuff
;;--------------------------------------------------------------------
(defrecord ClassHandle [handle parent name])

(defn list-classes
  "λ [config ← Map, package ← PackageHandle] → (Seq ClassHandle)

  Provides a mechanism for enumerating all of the classes in a given Java
  package. Returns an unordered seuqence of ClassHandles."

  [config package]
  {:pre [(instance? PackageHandle package)]}
  (list-type-filtered ->ClassHandle "class" config namespace))

;; Notes, Examples & things
;;--------------------------------------------------------------------
(defn thing->artifact [thing]
  (if-not (instance? ArtifactHandle thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->version [thing]
  (if-not (instance? VersionHandle thing)
    (when thing (recur (:parent thing)))
    thing))

(defn thing->path
  "λ [thing ← (Σ GroupHandle
                 ArtifactHandle
                 VersionHandle
                 NamespaceHandle
                 PackageHandle
                 DefHandle
                 ClassHandle)] → String

  Provides a mechanism for converting one of the Handle objects into a
  cannonical \"path\" which can be serialized, deserialized and walked back into
  a Handle."

  [thing]
  (->> thing
       (iterate :parent)
       (take-while identity)
       (reverse)
       (map :name)
       (interpose "/")
       (apply str)))

(def type-table
  {"group"    ->GroupHandle
   "artifact" ->ArtifactHandle
   "version"  ->VersionHandle
   "ns"       ->NamespaceHandle
   "package"  ->PackageHandle
   "var"      (fn [parent name]
                (->DefHandle parent name "var"))
   "fn"       (fn [parent name]
                (->DefHandle parent name "fn"))
   "macro"    (fn [parent name]
                (->DefHandle parent name "macro"))
   "symbol"   (fn [parent name]
                (->DefHandle parent name "symbol"))
   "class"    ->ClassHandle})

(defn path->thing
  "Attempts to construct the most deeply nested _documentation_ value possible given a"
  [config path]
  (let [parts (string/split path #"/")]
    (loop [parent       nil
           [part parts] parts]
      (if-not parent
        (let [typef (io/file )])
))

(defn thing->relative-path [class thing]
  (->> thing
       (iterate :parent)
       (take-while #(not (instance? class %1)))
       (reverse)
       (map :name)
       (interpose "/")
       (apply str)))

(defn thing->root-to [class thing]
  (->> thing
       (iterate :parent)
       (take-while identity)
       (reverse)
       (take-while #(not (instance? class %1)))
       (map :name)
       (interpose "/")
       (apply str)))

(defn thing->notes-handle

  [config {handle :handle :as thing}]
  (let [f (io/file handle notes-file)]
    (when (.exists f) f)))

(defn thing->url-handle

  [config {handle :handle :as thing}]
  (let [f (io/file handle url-file)]
    (if (.exists f) f
        (recur config (:parent thing)))))

(defn thing->docs-handle

  [config {handle :handle :as thing}]
  (let [f (io/file handle docs-file)]
    (when (.exists f) f)))

(defn thing->added-handle

  [config {handle :handle :as thing}]
  (let [f (io/file handle added-file)]
    (when (.exists f) f)))

(defn semver:in-range [v0 vf v]
  (and (>= (semver/cmp v0 v) 0)
       (<= (semver/cmp v vf) 0)))

(defn thing->example-handles

  [{{notes :notes} :datastore :as config} thing]
  (let [v0       (or (slurp (thing->added-handle thing))
                     "1.0.0")
        vf       (:name (thing->version thing))
        artifact (thing->artifact thing)
        versions (list-versions config artifact)
        apath    (thing->relative-path VersionHandle thing)
        bpath    (thing->root-to VersionHandle thing)]
    (for [v     versions
          :when (semver:in-range v0 vf (:name v))
          :let  [d (io/file (io/file notes bpath)
                            apath)]
          :when (and (.exists d)
                     (.isDirectory d))
          f     (.listFiles d)]
      f)))


;; DEV SHIT DO NOT COMMIT
(def config      {:datastore {:docs "doc-store/"}})
(def a-group     (first (list-groups     config           )))
(def a-artifact  (first (list-artifacts  config a-group   )))
(def a-version   (first (list-versions   config a-artifact)))
(def a-namespace (first (list-namespaces config a-version )))
(def a-def       (first (list-fns        config a-namespace)))
