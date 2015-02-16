(ns grimoire.things-test
  (:require [grimoire.things :as t]
            [clojure.test :refer :all]))

(def t
  (-> (t/->Group "foo")
      (t/->Artifact "bar")
      (t/->Version "1.0.0")
      (t/->Platform "clj")
      (t/->Ns "foo.bar")
      (t/->Def "qux")))

(defmacro is-assert-error? [x]
  `(is (~'thrown? AssertionError ~x)))

(deftest thing-conversion-test
  ;; Testing stuff that should work
  (is (t/group?     (t/thing->group t)))
  (is (t/artifact?  (t/thing->artifact t)))
  (is (t/version?   (t/thing->version t)))
  (is (t/platform?  (t/thing->platform t)))
  (is (t/namespace? (t/thing->namespace t)))
  (is (t/def?       (t/thing->def t))))

(deftest thing->def-tests
  ;; Testing stuff that should fail
  (testing "Testing thing->def"
    (is-assert-error?
     (t/thing->def
      (t/thing->namespace t)))

    (is-assert-error?
     (t/thing->def
      (t/thing->platform t)))

    (is-assert-error?
     (t/thing->def
      (t/thing->version t)))

    (is-assert-error?
     (t/thing->def
      (t/thing->artifact t)))

    (is-assert-error?
     (t/thing->def
      (t/thing->group t)))))

(deftest thing->namespace-tests
  (testing "Testing thing->namespace"
    (is-assert-error?
     (t/thing->namespace
      (t/thing->platform t)))

    (is-assert-error?
     (t/thing->namespace
      (t/thing->version t)))

    (is-assert-error?
     (t/thing->namespace
      (t/thing->artifact t)))

    (is-assert-error?
     (t/thing->namespace
      (t/thing->group t)))))

(deftest thing->platform-tests
  (testing "Testing thing->platform"
    (is-assert-error?
     (t/thing->platform
      (t/thing->version t)))

    (is-assert-error?
     (t/thing->platform
      (t/thing->artifact t)))

    (is-assert-error?
     (t/thing->platform
      (t/thing->group t)))))

(deftest thing->version-tests
  (testing "Testing thing->version"
    (is-assert-error?
     (t/thing->version
      (t/thing->artifact t)))

    (is-assert-error?
     (t/thing->version
      (t/thing->group t)))))

(deftest thing->artifact-tests
  (testing "Testing thing->artifact"
    (is-assert-error?
     (t/thing->artifact
      (t/thing->group t)))))
