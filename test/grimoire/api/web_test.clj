(ns grimoire.api.web-test
  (:require [grimoire.api :as api]
            [grimoire.things :as t]
            [grimoire.either :refer [succeed? result]]
            [grimoire.api.web.read]
            [clojure.test :refer :all]))

(def test-config
  {:datastore
   {:mode :web
    :host "http://127.0.0.1:3000"}}) ;; test against local Grimoire instance

;; Listing tests
;;------------------------------------------------------------------------------

(deftest list-groups-test
  (let [?res (-> test-config
                api/list-groups)]
    (is (succeed? ?res))
    (doseq [?g (result ?res)]
      (is (t/isa? :group ?g)))))

(deftest list-artifacts-test
  (let [g    (t/->Group "org.clojure")
        ?res (-> test-config
                (api/list-artifacts g))]
    (is (succeed? ?res))
    (doseq [?a (result ?res)]
      (is (t/isa? :artifact ?a)))))

(deftest list-versions-test
  (let [g    (t/->Group "org.clojure")
        a    (t/->Artifact g "clojure")
        ?res (-> test-config
                (api/list-versions a))]
    (is (succeed? ?res))
    (doseq [?v (result ?res)]
      (is (t/isa? :version ?v)))))

(deftest list-ns-test
  (let [v    (t/->Version "org.clojure" "clojure" "1.6.0")
        ?res (-> test-config
                (api/list-namespaces v))]
    (is (succeed? ?res))
    (doseq [?ns (result ?res)]
      (is (t/isa? :namespace ?ns)))))

(deftest list-prior-versions-test
  (let [ns   (t/->Ns "org.clojure" "clojure" "1.6.0" "clojure.core")
        ?res (-> test-config
                (api/thing->prior-versions ns))]
    (is (succeed? ?res))
    (is (= #{"1.6.0" "1.5.0" "1.4.0"}
           (->> ?res result (map (comp :name :parent)) set)))))

(deftest list-def-test
  (let [ns   (t/->Ns "org.clojure" "clojure" "1.6.0" "clojure.core")
        ?res (-> test-config
                (api/list-defs ns))]
    (is (succeed? ?res))
    (let [defs (->> ?res result (map :name) set)]
      (doseq [d ["for" "def" "let" "catch"]]
        (is (defs d))))))
