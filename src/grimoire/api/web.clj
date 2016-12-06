(ns grimoire.api.web
  (:require [guten-tag.core :refer [deftag]]
            [grimoire.things :as t]
            [grimoire.util :as util]))

(deftag Config
  "A configuration for the web backend. Stores the base URL for the
  API host."
  [host])

(def api-base-str "/api/v2/")

(def normalize-type
  {:json              "json"
   "json"             "json"
   :application/json  "json"
   "application/json" "json"

   :edn               "edn"
   "edn"              "edn"
   :application/edn   "edn"
   "application/edn"  "edn"})

(defn make-api-url
  "λ [Cfg, Thing, Op] → String

  Forges a Grimoire API EDN request for a given Thing and Op."
  [config thing op]
  {:pre [(Config? config)
         (or (t/thing? thing)
             (nil? thing))
         (string? op)]}
  (str (:host config)
       api-base-str
       (when thing (t/thing->url-path thing util/url-munge))
       "?op=" op
       (when-let [type (:type config :edn)]
         (when-let [t' (normalize-type type)]
           (str "&type=" t')))))

(def store-base-str "/store/v1/")

(defn make-html-url
  "λ [Cfg, Thing] → String

  Forges a Grimoire URL for a given Thing.  No validation is done to ensure that
  the target Grimoire instance _has_ the Thing in question."
  [config thing]
  {:pre [(Config? config)
         (or (t/thing? thing)
             (nil? thing))]}
  (str (:host config)
       store-base-str
       (when thing (t/thing->url-path thing util/url-munge))))
