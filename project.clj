(defproject org.clojure-grimoire/lib-grimoire "0.8.0-SNAPSHOT"
  :description "A shared library for Grimoire infrastructure"
  :url "http://github.com/clojure-grimoire/lib-grimoire"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 [version-clj "0.1.0"
                  :exclusions [org.clojure/clojure]]
                 [me.arrdem/detritus "0.2.2-SNAPSHOT"
                  :exclusions [org.clojure/clojure]]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.6.1"
                                   :exclusions [org.clojure/clojure]]]}})
