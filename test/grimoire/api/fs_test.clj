(ns grimoire.api.fs-test
  (:require [grimoire.api :as api]
            [grimoire.things :as t]
            [grimoire.either :refer [result]]
            [grimoire.api.fs :refer [->Config]]
            [grimoire.api.fs.read]
            [grimoire.api.fs.write]
            [clojure.test :refer :all]))

(def test-config
  (->Config "resources/test/docs/"
            "resources/test/notes/"
            "resources/test/notes/"))

(def test-resources
  [
   "org.foo"
   "org.foo/a"
   "org.foo/a/0.1.0-SNAPSHOT/clj/a.core"
   "org.foo/a/0.1.0/clj/a.core"
   "org.foo/a/1.0.0/clj/a.core"
   "org.foo/a/1.0.1/clj/a.core"
   "org.foo/a/1.1.0/clj"
   "org.foo/a/1.1.0/clj/a.core"
   "org.foo/a/1.1.0/clj/a.core/foo"
   "org.foo/a/1.1.0/clj/a.core/qux"
   "org.foo/a/1.1.0/clj/a.impl.clj"
   "org.foo/a/1.1.0/cljclr"
   "org.foo/a/1.1.0/cljs"
   "org.foo/b"
   "org.bar/b/1.0.0/clj/not-qux/c"
   "org.bar/b/1.0.0/clj/not-qux"
   "org.bar/b/1.0.0/clj"
   "org.bar/b/1.0.0/"
   "org.bar/b"
   "org.bar"
   ])

(doseq [uri test-resources]
  (let [t (t/path->thing uri)]
    (api/write-meta test-config t {})))

;; Listing tests
;;------------------------------------------------------------------------------

(deftest list-groups-test
  (let [groups (-> test-config
                   api/list-groups
                   result)]
    (is (= ["org.bar" "org.foo"]
           (sort (map t/thing->name groups))))))

(deftest list-artifacts-test
  (let [g       (t/->Group "org.foo")
        members (-> test-config
                    (api/list-artifacts g)
                    result)]
    (is (= ["a" "b"]
           (sort (mapv t/thing->name members))))))

(deftest list-versions-test
  (let [a        (-> (t/->Group "org.foo")
                     (t/->Artifact "a"))
        versions (-> test-config
                     (api/list-versions a)
                     result)]
    (is (= ["0.1.0" "0.1.0-SNAPSHOT" "1.0.0" "1.0.1" "1.1.0"]
           (sort (mapv t/thing->name versions))))))

(deftest list-platform-test
  (let [p (-> (t/->Group "org.foo")
              (t/->Artifact "a")
              (t/->Version "1.1.0"))
        platforms (-> test-config
                      (api/list-platforms p)
                      result)]
    (is (= ["clj" "cljclr" "cljs"]
           (sort (map t/thing->name platforms))))))

(deftest list-ns-test
  (let [v   (-> (t/->Group "org.foo")
                (t/->Artifact "a")
                (t/->Version "1.1.0")
                (t/->Platform "clj"))
        nss (-> test-config
                (api/list-namespaces v)
                result)]
    (is (= ["a.core" "a.impl.clj"]
           (sort (mapv t/thing->name nss))))))

(deftest list-prior-versions-test
  (let [ns   (-> (t/->Group "org.foo")
                 (t/->Artifact "a")
                 (t/->Version "1.1.0")
                 (t/->Platform "clj")
                 (t/->Ns "a.core"))
        defs (-> test-config
                 (api/thing->prior-versions ns)
                 result)]
    (is (= ["org.foo/a/0.1.0-SNAPSHOT/clj/a.core"
            "org.foo/a/0.1.0/clj/a.core"
            "org.foo/a/1.0.0/clj/a.core"
            "org.foo/a/1.0.1/clj/a.core"
            "org.foo/a/1.1.0/clj/a.core"]
           (sort (mapv t/thing->path defs))))))

(deftest list-def-test
  (let [ns   (-> (t/->Group "org.foo")
                 (t/->Artifact "a")
                 (t/->Version "1.1.0")
                 (t/->Platform "clj")
                 (t/->Ns "a.core"))
        defs (-> test-config
                 (api/list-defs ns)
                 result)]
    (is (= ["foo" "qux"]
           (sort (map t/thing->name defs))))))

;; Reading/Writing tests
;;------------------------------------------------------------------------------

(def p  ["org.bar" "b" "1.0.0" "clj" "not-qux" "c"])

(deftest read-write-meta-test
  (let [meta {:val (rand-int Integer/MAX_VALUE)}]
    (doseq [p (reverse (take 5 (iterate butlast p)))]
      (let [path  (apply str (interpose "/" p))
            thing (t/path->thing path)]
        ;;----------------------------------------
        (api/write-meta test-config thing meta)
        (is (= (result (api/read-meta test-config thing)) meta)
            (str "Failed to read meta from " (t/thing->path thing)))))))

(deftest read-write-notes-test
  (let [notes "Some test notes"]
    (doseq [p (reverse (take 3 (iterate butlast p)))]
      (let [path  (apply str (interpose "/" p))
            thing (t/path->thing path)]
        ;;----------------------------------------
        (api/write-note test-config thing notes)
        (is (= notes
               (-> test-config
                   (api/read-notes thing)
                   result first second)))))))
