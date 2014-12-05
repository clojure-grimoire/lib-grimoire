(ns grimoire.util-test
  (:require [grimoire.util :as util]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(defspec munge-unmunge-is-isomorphism 1000
  (prop/for-all [s gen/string]
    (= s (-> s util/munge util/demunge))))
