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
  (:require [grimoire.api :as api]
            [grimoire.util :as u]
            [grimoire.either :refer [with-result succeed? result succeed fail either?]]
            [grimoire.things :as t]
            [grimoire.api.web :as web]
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
  {:pre [(web/Config? config)
         (or (t/thing? thing)
             (nil? thing))
         (string? op)]}
  (str (:host config)
       baseurl
       (when thing (t/thing->url thing))
       "?op=" op "&type=edn"))

(defn do-data-req
  "λ [Cfg → Thing → Op] → Either[Success[t], Failure[String]]

  Forges and executes a data request agains the Grimoire web API as specified by
  the various arguments. Returns the entire result of the Grimoire request
  unaltered and wrapped in Either."
  [config thing op]
  {:post [(either? %)]}
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
(defmethod api/-list-groups ::web/Config [config]
  (do-thing-req config "groups" t/->Group nil))

(defmethod api/-list-artifacts ::web/Config [config group-thing]
  (do-thing-req config "artifacts" t/->Artifact group-thing))

(defmethod api/-list-versions ::web/Config [config artifact-thing]
  (do-thing-req config "versions" t/->Version artifact-thing))

(defmethod api/-list-namespaces ::web/Config [config version-thing]
  (do-thing-req config "namespaces" t/->Ns version-thing))

(defmethod api/-list-defs ::web/Config [config namespace-thing]
  (do-thing-req config "all" t/->Def namespace-thing))

(defmethod api/-read-note ::web/Config [config thing]
  (do-data-req config thing "notes"))

(defmethod api/-read-example ::web/Config [config ex-thing]
  {:pre [(t/example? ex-thing)]}
  (do-data-req config ex-thing "example"))

(defmethod api/-read-meta ::web/Config [config thing]
  (do-data-req config thing "meta"))

(defmethod api/-list-related ::web/Config [config def-thing]
  {:pre [(t/def? def-thing)]}
  ;; FIXME: not implemented on the Grimoire side see clojure-grimoire/grimoire#152
  ;; Grimoire will yeild Succeed[Seq[qualifiedSymbol]]
  (let [version (t/thing->version def-thing)
        ?res    (do-data-req config def-thing "related")]
    (if (succeed? ?res)
      (->> ?res result
           (map (comp t/path->thing :uri))
           succeed)
      ?res)))
