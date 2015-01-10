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
            [clojure.edn :as edn]
            [version-clj.core :as semver]))

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------

(def baseurl "/api/v0/")

(defn grim-succeed? [result]
  (= (:result result) :success))

(defn grim-result [result]
  (:body result))

(defn make-request [config thing op]
  (str (-> config :datastore :host)
       baseurl (:uri thing)
       "?op=" op "&type=edn"))

(defn do-data-req [config thing op]
  {:post [either?]}
  (let [?res (-> (make-request config thing op)
                slurp
                edn/read-string)]
    ((if (grim-succeed? ?res)
       succeed fail)
     (grim-result ?res))))

(defn do-thing-req [config op ctor parent]
  (let [?res (do-data-req config parent op)]
    (if (succeed? ?res)
      (->> ?res result
         (map (comp (partial ctor parent) :name))
         succeed)
      ?res)))

(defmethod api/list-groups :web [config]
  (do-thing-req config "groups" ->Group nil))

(defmethod api/list-artifacts :web [config group-thing]
  (do-thing-req config "artifacts" ->Artifact group-thing))

(defmethod api/list-versions :web [config artifact-thing]
  (do-thing-req config "versions" ->Version artifact-thing))

(defmethod api/list-namespaces :web [config version-thing]
  (do-thing-req config "namespaces" ->Ns version-thing))

(defmethod api/list-defs :web [config namespace-thing]
  (do-thing-req config "all" ->Def namespace-thing))

(defmethod api/thing->prior-versions :web [config thing]
  ;; FIXME: this is entirely common to fs/read's thing->versions
  {:pre [(#{:version :namespace :def} (:type thing))]}
  (let [thing    (ensure-thing thing)
        currentv (thing->version thing)               ; version handle
        current  (normalize-version (:name currentv)) ; version string
        added    (-> (api/read-meta config thing)      ; FIXME: can Fail
                    result                            ; FIXME: can throw AssertionException
                    (get :added "0.0.0")
                    normalize-version)                ; version string
        versions (->> (:parent currentv)
                    (api/list-versions config))
        unv-path (thing->relative-path :version thing)]
    (if (succeed? versions)
      (-> (for [v     (result versions)
               :when (<= 0 (semver/version-compare (:name v) added))
               :when (>= 0 (semver/version-compare (:name v) current))]
           ;; FIXME: this could be a direct constructor given an
           ;; appropriate vehicle for doing so since the type is directed
           ;; and single but may not generally make sense if this is not
           ;; the case.
           (path->thing (str (thing->path v) "/" unv-path)))
         succeed)

      ;; versions is a Fail, pass it down
      versions)))

(defmethod api/read-notes :web [config thing]
  {:pre [(isa? :def thing)]}
  (do-data-req config thing "notes"))

(defmethod api/read-examples :web [config def-thing]
  {:pre [(isa? :def def-thing)]}
  (do-data-req config def-thing "examples"))

(defmethod api/read-meta :web [config thing]
  (do-data-req config thing "meta"))

(defmethod api/read-related :web [config def-thing]
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
