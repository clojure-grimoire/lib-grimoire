(ns grimoire.api.web.read
  "This namespace implements the reading and listing parts of the
  Grimoire API using the Grimoire webservice as its backend.

  Writing to this datastore is not supported yet™.
  `list-classes` is not supported yet™.
  `list-defs` lists all defs, not just defs of a type."
  (:refer-clojure :exclude [isa?])
  (:require [grimoire.api :as api]
            [grimoire.util :refer [normalize-version succeed? result succeed fail]]
            [grimoire.things :refer :all]
            [clojure.edn :as edn]
            [version-clj.core :as semver]))

;; Interacting with the datastore - reading
;;--------------------------------------------------------------------

(def baseurl "http://conj.io/api/v0/")

(defn grim-succeed? [result]
  (= (:result result) :success))

(defn grim-result [result]
  (:body result))

(defn make-request [thing op]
  (str baseurl (:uri thing) "?op=" op "&type=edn"))

(defmethod api/list-groups :web [config]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  (let [?res (->  nil
                 (make-request "groups")
                 slurp
                 edn/read-string)]
    (if (grim-succeed? ?res)
      (->> ?res grim-result
         (map (comp ->Group :name))
         succeed)
      (fail (grim-result ?res)))))

(defmethod api/list-artifacts :web [config group-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  (let [?res (-> group-thing
                (make-request "artifacts")
                slurp
                edn/read-string)]
    (if (grim-succeed? ?res)
      (->> ?res grim-result
         (map (comp (partial ->Artifact group-thing) :name))
         succeed)
      (fail (grim-result ?res)))))

(defmethod api/list-versions :web [config artifact-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  (let [?res (-> artifact-thing
                (make-request "versions")
                edn/read-string)]
    ((if (grim-succeed? ?res)
       succeed fail)
     (grim-result ?res))))

(defmethod api/list-namespaces :web [config version-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  (-> version-thing
     (make-request "namespaces")
     edn/read-string))

(defmethod api/list-defs :web [config namespace-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  (-> namespace-thing
     (make-request "all")
     edn/read-string))

(defmethod api/thing->prior-versions :web [config thing]
  {:pre [(#{:version :namespace :def} (:type thing))]}
  (let [thing    (ensure-thing thing)
        currentv (thing->version thing)               ; version handle
        current  (normalize-version (:name currentv)) ; version string
        added    (-> (api/read-meta config thing)
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

(defmethod api/thing->prior-versions :web [config thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  ;; FIXME
  )

(defmethod api/read-notes :web [config thing]
  ;; FIXME: Needs to return a Succeed[Map]
  (-> thing
     (make-request "notes")
     edn/read-string))

(defmethod api/read-examples :web [config def-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Example]]
  ;; FIXME: should check that def-thing is a def
  (-> def-thing
     (make-request "examples")
     edn/read-string))

(defmethod api/read-meta :web [config thing]
  ;; FIXME: Needs to return a Succeed[Meta]
  (-> thing
     (make-request "meta")
     edn/read-string))

(defmethod api/read-related :web [config def-thing]
  ;; FIXME: Needs to return a Succeed[Seq[Thing]]
  ;; FIXME: should check that def-thing is a def
  ;; FIXME: not implemented on the Grimoire side see clojure-grimoire/grimoire#152
  (-> def-thing
     (make-request "related")))
