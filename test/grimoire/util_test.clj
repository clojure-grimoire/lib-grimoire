(ns grimoire.util-test
  (:require [grimoire.util :as u]
            [clojure.test :refer :all]))

(deftest update-munge
  (is (= (u/munge "-") (u/update-munge "_DASH_")))
  (is (= (u/munge "!") (u/update-munge "_BANG_")))
  (is (= (u/munge "*") (u/update-munge "_STAR_")))
  (is (= (u/munge "=") (u/update-munge "_EQ_")))
  (is (= (u/munge "<") (u/update-munge "_LT_")))
  (is (= (u/munge ">") (u/update-munge "_GT_"))))

(deftest normalize-version-test
  (is (= "1.0.0" (u/normalize-version "1.0"))))
