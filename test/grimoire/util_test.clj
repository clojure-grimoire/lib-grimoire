(ns grimoire.util-test
  (:require [grimoire.util :as u]
            [clojure.test :refer :all]))

(deftest munge-test
  (is (= "QMARK" (u/munge "?")))
  (is (= "DOT"   (u/munge ".")))
  (is (= "SLASH" (u/munge "/"))))

(deftest update-munge
  (is (= "-" (u/update-munge "_DASH_")))
  (is (= "!" (u/update-munge "_BANG_")))
  (is (= "*" (u/update-munge "_STAR_")))
  (is (= "=" (u/update-munge "_EQ_")))
  (is (= "<" (u/update-munge "_LT")))
  (is (= ">" (u/update-munge "_GT"))))

(deftest normalize-version-test
  (is (= "1.0.0" (u/normalize-version "1.0"))))
