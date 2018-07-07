(ns grimoire.util-test
  (:require [grimoire.util :as u]
            [clojure.test :refer :all]))

(deftest update-munge
  (are [a b] (= (u/munge a) (u/munge (u/update-munge b)))
    "-" "_DASH_"
    "!" "_BANG_"
    "*" "_STAR_"
    "=" "_EQ_"
    "<" "_LT_"
    ">" "_GT_"))

(deftest normalize-version-test
  (is (= "1.0.0" (u/normalize-version "1.0"))))

(deftest version-cmp-test
  (testing "Testing version parsing"
    (are [expected str] (= expected (u/clojure-version->cmp-key str))
      [1 6 0 "alpha" 1]        "1.6.0-alpha1"
      [1 6 0 "zfinal" nil]     "1.6.0"
      [1 8 0 "zfinal" nil]     "1.8.0"
      [100 234 123 "beta" nil] "100.234.123-beta"
      [0 3 0 "alpha" 1]        "0.3-alpha1"
      [0 3 0 "alpha" 15]       "0.3-ALPHA15"))

  (testing "Testing version comparison"
    (are [l pred r] (pred (compare (u/clojure-version->cmp-key l) (u/clojure-version->cmp-key r)))
      "1.6.0"        zero? "1.6.0"
      "1.6.0-alpha1" neg?  "1.6.0"
      "1.9.0-beta2"  pos?  "1.6.0"
      "1.9.0-beta2"  pos?  "1.9.0-beta1"
      "1.8.0"        pos?  "1.6.0")))
