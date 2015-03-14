(ns grimoire.api.web
  (:require [detritus.variants :refer [deftag]]))

(deftag Config
  "A configuration for the web backend. Stores the base URL for the
  API host."
  [host])
