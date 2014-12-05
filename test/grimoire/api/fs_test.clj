(ns grimoire.api.fs-test
  (:require [grimoire.api :as api]
            [grimoire.things :as t]
            [grimoire.api.fs]
            [grimoire.api.fs.read]
            [grimoire.api.fs.write]
            [clojure.test :refer :all]))

(def test-config
  {:datastore
   {:docs  "resources/test/docs/"
    :notes "resources/test/notes/"
    :mode  :filesystem}})

(deftest list-groups-test
  (is (= ["org.bar" "org.foo"]
         (sort (map :name (api/list-groups test-config))))))

(deftest list-artifacts-test
  (let [g       (t/->Group "org.foo")
        members (api/list-artifacts test-config g)]
    (is (= ["a" "b"]
           (sort (map :name members))))))

(deftest list-versions-test
  (let [g        (t/->Group "org.foo")
        a        (t/->Artifact g "a")
        versions (api/list-versions test-config a)]
    (is (= ["0.1.0" "0.1.0-SNAPSHOT" "1.0.0" "1.0.1" "1.1.0"]
           (sort (map :name versions))))))

(deftest list-ns-test
  (let [v    (t/->Version "org.foo" "a" "1.0.0")
        defs (api/list-namespaces test-config v)]
    (is (= ["a.core" "a.impl.clj" "a.impl.cljs"]
           (sort (map :name defs))))))
