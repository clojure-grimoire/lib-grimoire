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
   "org.foo/a/0.1.0-SNAPSHOT"
   "org.foo/a/0.1.0-SNAPSHOT/clj"
   "org.foo/a/0.1.0-SNAPSHOT/clj/a.core"
   "org.foo/a/0.1.0-SNAPSHOT/clj/a.core/foo"

   "org.foo/a/0.1.0"
   "org.foo/a/0.1.0/clj"
   "org.foo/a/0.1.0/clj/a.core"
   "org.foo/a/0.1.0/clj/a.core/foo"

   "org.foo/a/1.0.0"
   "org.foo/a/1.0.0/clj"
   "org.foo/a/1.0.0/clj/a.core"
   "org.foo/a/1.0.0/clj/a.core/foo"

   "org.foo/a/1.0.1"
   "org.foo/a/1.0.1/clj"
   "org.foo/a/1.0.1/clj/a.core"
   "org.foo/a/1.0.1/clj/a.core/foo"
   
   "org.foo/a/1.1.0"
   "org.foo/a/1.1.0/clj"
   "org.foo/a/1.1.0/clj/a.core"
   "org.foo/a/1.1.0/clj/a.core/foo?"
   "org.foo/a/1.1.0/clj/a.core/foo"
   "org.foo/a/1.1.0/clj/a.core/qux"
   "org.foo/a/1.1.0/clj/a.core/qux!"
   "org.foo/a/1.1.0/clj/a.core/qux."
   "org.foo/a/1.1.0/clj/a.impl.clj"
   "org.foo/a/1.1.0/pixi"
   "org.foo/a/1.1.0/pixi/a.core"
   "org.foo/a/1.1.0/pixi/a.core/qux."
   "org.foo/a/1.1.0/cljs"
   "org.foo/a/1.1.0/cljs/a.core"
   "org.foo/a/1.1.0/cljs/a.core/qux."
   "org.foo/a/1.1.0/cljclr"
   "org.foo/a/1.1.0/cljclr/a.core"
   "org.foo/a/1.1.0/cljclr/a.core/qux."
   "org.foo/a/1.1.0/ox"
   "org.foo/a/1.1.0/ox/a.core"
   "org.foo/a/1.1.0/ox/a.core/qux."
   "org.foo/a/1.1.0/toc"
   "org.foo/a/1.1.0/toc/a.core"
   "org.foo/a/1.1.0/toc/a.core/qux."
   "org.foo/a/1.1.0/cljclr"
   "org.foo/a/1.1.0/cljs"

   "org.foo/b"
   
   "org.bar"
   "org.bar/b"
   "org.bar/b/1.0.0/"
   "org.bar/b/1.0.0/clj"
   "org.bar/b/1.0.0/clj/not-qux"
   "org.bar/b/1.0.0/clj/not-qux/c"
   ])

(doseq [uri test-resources]
  (let [t (t/path->thing uri)]
    (api/write-meta test-config t {})))

;; Listing tests
;;------------------------------------------------------------------------------

(deftest list-groups-test
  (let [groups  (-> test-config
                    api/list-groups
                    result)
        sgroups (-> test-config
                    (api/search [:group nil])
                    result)]
    (is (= ["org.bar" "org.foo"]
           (sort (map t/thing->name groups))
           (sort (map t/thing->name sgroups))))))

(deftest list-artifacts-test
  (let [g        (t/->Group "org.foo")
        members  (-> test-config
                     (api/list-artifacts g)
                     result)
        smembers (-> test-config
                     (api/search [:artifact "org.foo" nil])
                     result)]
    (is (= ["a" "b"]
           (sort (mapv t/thing->name members))
           (sort (mapv t/thing->name smembers))))))

(deftest list-versions-test
  (let [a         (-> (t/->Group "org.foo")
                      (t/->Artifact "a"))
        versions  (-> test-config
                      (api/list-versions a)
                      result)
        sversions (-> test-config
                      (api/search [:version "org.foo" "a" nil])
                      result)]
    (is (= ["0.1.0" "0.1.0-SNAPSHOT" "1.0.0" "1.0.1" "1.1.0"]
           (sort (mapv t/thing->name versions))
           (sort (mapv t/thing->name sversions))))))

(deftest list-platforms-test
  (let [p          (-> (t/->Group "org.foo")
                       (t/->Artifact "a")
                       (t/->Version "1.1.0"))
        platforms  (-> test-config
                       (api/list-platforms p)
                       result)
        splatforms (-> test-config
                       (api/search [:platform "org.foo" "a" "1.1.0" nil])
                       result)]
    (is (= ["clj" "cljclr" "cljs" "ox" "pixi" "toc"]
           (sort (map t/thing->name platforms))
           (sort (map t/thing->name splatforms))))))

(deftest list-ns-test
  (let [v    (-> (t/->Group "org.foo")
                 (t/->Artifact "a")
                 (t/->Version "1.1.0")
                 (t/->Platform "clj"))
        nss  (-> test-config
                 (api/list-namespaces v)
                 result)
        snss (-> test-config
                 (api/search [:ns "org.foo" "a" "1.1.0" "clj" nil])
                 result)]
    (is (= ["a.core" "a.impl.clj"]
           (sort (mapv t/thing->name nss))
           (sort (mapv t/thing->name snss))))))

(deftest list-prior-versions-test
  (let [ns       (-> (t/->Group "org.foo")
                     (t/->Artifact "a")
                     (t/->Version "1.1.0")
                     (t/->Platform "clj")
                     (t/->Ns "a.core"))
        versions (-> test-config
                     (api/thing->prior-versions ns)
                     result)]
    (is (= ["org.foo/a/0.1.0-SNAPSHOT/clj/a.core"
            "org.foo/a/0.1.0/clj/a.core"
            "org.foo/a/1.0.0/clj/a.core"
            "org.foo/a/1.0.1/clj/a.core"
            "org.foo/a/1.1.0/clj/a.core"]
           (sort (mapv t/thing->path versions))))))

(deftest list-def-test
  (let [ns    (-> (t/->Group "org.foo")
                  (t/->Artifact "a")
                  (t/->Version "1.1.0")
                  (t/->Platform "clj")
                  (t/->Ns "a.core"))
        defs  (-> test-config
                  (api/list-defs ns)
                  result)
        sdefs (-> test-config
                  (api/search [:def "org.foo" "a" "1.1.0" "clj" "a.core" nil])
                  result)]
    (is (= ["foo" "foo?" "qux" "qux!" "qux."]
           (sort (map t/thing->name defs))
           (sort (map t/thing->name sdefs))))))

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
