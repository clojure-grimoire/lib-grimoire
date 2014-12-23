(defproject org.clojure-grimoire/lib-grimoire (slurp "VERSION")
  :description "A shared library for Grimoire infrastructure"
  :url "http://github.com/clojure-grimoire/lib-grimoire"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [version-clj "0.1.0"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.6.1"]
                                  [org.clojure-grimoire/lein-grim "[0.3.2,)"]]
                   :aliases {"grim" ["run" "-m" "grimoire.doc"
                                     ,,:project/groupid
                                     ,,:project/artifactid
                                     ,,:project/version
                                     ,,nil]}}})
