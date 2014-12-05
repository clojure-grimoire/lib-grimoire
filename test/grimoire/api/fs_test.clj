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

;; Listing tests
;;------------------------------------------------------------------------------

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
  (let [v   (t/->Version "org.foo" "a" "1.0.0")
        nss (api/list-namespaces test-config v)]
    (is (= ["a.core" "a.impl.clj" "a.impl.cljs"]
           (sort (map :name nss))))))

(deftest list-def-test
  (let [ns (t/->Ns "org.foo" "a" "1.0.0" "a.core")
        defs (api/list-defs test-config ns)]
    (is (= ["foo" "qux"]
           (sort (map :name defs))))))

;; Writing tests
;;------------------------------------------------------------------------------

(deftest write-meta-test
  (let [n  (rand-int Integer/MAX_VALUE)
        p  ["org.bar" "b" "1.0.0" "not-qux" "c"]
        ps (take 5 (iterate butlast p))]
    (doseq [p ps]
      (let [path  (apply str (interpose "/" p))
            thing (t/path->thing path)
            meta  {:val n}]
        (api/write-meta test-config thing meta)
        (is (= (api/read-meta test-config thing)
               meta))))))
