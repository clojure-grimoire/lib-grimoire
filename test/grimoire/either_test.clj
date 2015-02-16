(ns grimoire.either-test
  (:require [grimoire.either :as either]
            [clojure.test :refer :all]))

(deftest result-tests
  (let [r "foo bar baz"]
    (is (= r (either/result (either/succeed r))))
    (is (either/succeed? (either/succeed r)))

    (is (= r (either/message (either/fail r))))
    (is (either/fail? (either/fail r)))))
