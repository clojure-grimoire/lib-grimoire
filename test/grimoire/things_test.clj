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

(defmacro is-nil? [x]
  `(is (~'nil? ~x)))

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
    (is-nil?
     (t/thing->def
      (t/thing->namespace t)))

    (is-nil?
     (t/thing->def
      (t/thing->platform t)))

    (is-nil?
     (t/thing->def
      (t/thing->version t)))

    (is-nil?
     (t/thing->def
      (t/thing->artifact t)))

    (is-nil?
     (t/thing->def
      (t/thing->group t)))))

(deftest thing->namespace-tests
  (testing "Testing thing->namespace"
    (is-nil?
     (t/thing->namespace
      (t/thing->platform t)))

    (is-nil?
     (t/thing->namespace
      (t/thing->version t)))

    (is-nil?
     (t/thing->namespace
      (t/thing->artifact t)))

    (is-nil?
     (t/thing->namespace
      (t/thing->group t)))))

(deftest thing->platform-tests
  (testing "Testing thing->platform"
    (is-nil?
     (t/thing->platform
      (t/thing->version t)))

    (is-nil?
     (t/thing->platform
      (t/thing->artifact t)))

    (is-nil?
     (t/thing->platform
      (t/thing->group t)))))

(deftest thing->version-tests
  (testing "Testing thing->version"
    (is-nil?
     (t/thing->version
      (t/thing->artifact t)))

    (is-nil?
     (t/thing->version
      (t/thing->group t)))))

(deftest thing->artifact-tests
  (testing "Testing thing->artifact"
    (is-nil?
     (t/thing->artifact
      (t/thing->group t)))))

(deftest thing->root-to-tests
  (is (= (t/thing->path (t/thing->platform t))
         (t/thing->root-to t/platform t))))

(deftest ensure-thing-tests
  (is (= t (t/ensure-thing (t/thing->path t))))
  (is (= t (t/ensure-thing t)))
  (is (thrown? Exception (t/ensure-thing 1))))
