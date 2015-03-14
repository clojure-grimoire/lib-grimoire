(ns grimoire.either-test
  (:require [grimoire.either :as either]
            [clojure.test :refer :all]))

(deftest result-tests
  (let [r "foo bar baz"]
    (is (= r (either/result (either/succeed r))))
    (is (either/succeed? (either/succeed r)))

    (is (= r (either/message (either/fail r))))
    (is (either/fail? (either/fail r)))

    (is (either/either? (either/succeed r)))
    (is (either/either? (either/fail r)))))

(deftest simple-with-result-test
  (is (= \h
         (either/with-result [x (either/succeed "hax! I call hax!")]
           (first x)))))

(deftest complex-with-result-test
  (let [f (fn [x]
            (either/with-result [a (x)]
              (either/succeed (inc a))
              (either/fail (str "Nested falure: " a " In fn " x))))]
    (is (= (either/succeed 2)
           (f #(either/succeed 1))))
    (is (either/fail? (f #(either/fail "Fuck off!"))))))
