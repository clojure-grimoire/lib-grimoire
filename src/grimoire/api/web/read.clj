(ns grimoire.api.web.read
  "This namespace implements the reading and listing parts of the Grimoire API
  using the Grimoire webservice as its backend.

  Writing to this datastore is not supported yet™.
  `list-classes` is not supported yet™.
  `list-defs` lists all defs, not just defs of a type.

  To use this backend, you will need to load this namespace, and then invoke the
  API with a configuration map as follows:
  
  {:datastore
   {:mode :web,
    :host \"http://conj.io\"}

  Note that the host need not be conj.io, but must host a Grimoire 0.4 or later
  instance providing the v0 API. The host string should include a http or https
  protocol specifier as appropriate and should not end in a /."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.api :as api]
            [grimoire.util :refer [normalize-version]]
            [grimoire.either :refer [with-result succeed? result succeed fail either?]]
            [grimoire.things :refer :all]
            [clojure.edn :as edn]))

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------

(def baseurl "/api/v1/")

(defn grim-succeed?
  "λ [t] → Bool

  Helper, indicates whether a Grimoire web result succeeded or failed."
  [result]
  (= (:result result) :success))

(defn grim-result
  "λ [t] → y

  Helper, destructures out the body of a Grimoire web result."
  [result]
  (:body result))

(defn make-request
  "λ [Cfg → Thing → Op] → String

  Forges a Grimoire API V0 request for a given Thing and Op."
  [config thing op]
  (str (-> config :datastore :host)
       baseurl
       (when thing (thing->path thing))
       "?op=" op "&type=edn"))

(defn do-data-req
  "λ [Cfg → Thing → Op] → Either[Success[t], Failure[String]]

  Forges and executes a data request agains the Grimoire web API as specified by
  the various arguments. Returns the entire result of the Grimoire request
  unaltered and wrapped in Either."
  [config thing op]
  {:post [either?]}
  (let [?res (-> (make-request config thing op)
                 slurp
                 edn/read-string)]
    ((if (grim-succeed? ?res)
       succeed fail)
     (grim-result ?res))))

(defn do-thing-req
  "λ [Cfg → Op → (λ [p → String] → c) → p ⊆ Thing] → Either[Success[Seq[c]], Failure[String]]

  Helper, does a data request against the Grimoire web API as specified by the
  config and op, running the request results through the constructor to yield a
  seq of Things as constructed from the pair (parent, (:name result))."
  [config op ctor parent]
  (let [?res (do-data-req config parent op)]
    (if (succeed? ?res)
      (->> ?res result
           (map (comp (partial ctor parent) :name))
           succeed)
      ?res)))

;; API imp'l
;;--------------------------------------------------------------------

(defn list-groups
  "Implementation of grimoire.api/list-groups. This function should not be used
  directly, please use the wrapper multimethod in grimoire.api."
  [config]
  (do-thing-req config "groups" ->Group nil))

(defmethod api/list-groups :web [config]
  (list-groups config))

(defn list-artifacts
  "Implementation of grimoire.api/list-artifacts. This function should not be
  used directly, please use the wrapper multimethod in grimoire.api."
  [config group-thing]
  (do-thing-req config "artifacts" ->Artifact group-thing))

(defmethod api/list-artifacts :web [config group-thing]
  (list-artifacts config group-thing))

(defn list-versions
  "Implementation of grimoire.api/list-versions. This function should not be
  used directly, please use the wrapper multimethod in grimoire.api."
  [config artifact-thing]
  (do-thing-req config "versions" ->Version artifact-thing))

(defmethod api/list-versions :web [config artifact-thing]
  (list-versions config artifact-thing))

(defn list-namespaces
  "Implementation of grimoire.api/list-namespaces. This function should not be
  used directly, please use the wrapper multimethod in grimoire.api."
  [config version-thing]
  (do-thing-req config "namespaces" ->Ns version-thing))

(defmethod api/list-namespaces :web [config version-thing]
  (list-namespaces config version-thing))

(defn list-defs
  "Implementation of grimoire.api/list-defs. This function should not be
  used directly, please use the wrapper multimethod in grimoire.api."
  [config namespace-thing]
  (do-thing-req config "all" ->Def namespace-thing))

(defmethod api/list-defs :web [config namespace-thing]
  (list-defs config namespace-thing))

(defn read-notes
  "Implementation of grimoire.api/read-notes. This function should not be used
  directly, please use the wrapper multimethod in grimoire.api."
  [config thing]
  {:pre [(isa? :def thing)]}
  (do-data-req config thing "notes"))

(defmethod api/read-notes :web [config thing]
  (read-notes config thing))

(defn read-examples
  "Implementation of grimoire.api/read-examples. This function should
  not be used directly, please use the wrapper multimethod in
  grimoire.api."
  [config def-thing]
  {:pre [(isa? :def def-thing)]}
  (do-data-req config def-thing "examples"))

(defmethod api/read-examples :web [config def-thing]
  (read-examples config def-thing))

(defn read-meta
  "Implementation of grimoire.api/read-meta. This function should not
  be used directly, please use the wrapper multimethod in
  grimoire.api."
  [config thing]
  (do-data-req config thing "meta"))

(defmethod api/read-meta :web [config thing]
  (read-meta config thing))

(defn read-related
  "Implementation of grimoire.api/read-related. This function should not
  be used directly, please use the wrapper multimethod in
  grimoire.api."
  [config def-thing]
  {:pre [(isa? :def def-thing)]}
  ;; FIXME: not implemented on the Grimoire side see clojure-grimoire/grimoire#152
  ;; Grimoire will yeild Succeed[Seq[qualifiedSymbol]]
  (let [version (thing->version def-thing)
        ?res    (do-data-req config def-thing "related")]
    (if (succeed? ?res)
      (->> ?res result
           (map (comp path->thing :uri))
           succeed)
      ?res)))

(defmethod api/read-related :web [config def-thing]
  (read-related config def-thing))
