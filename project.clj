(defproject org.clojure-grimoire/lib-grimoire "0.9.1"
  :description "A shared library for Grimoire infrastructure"
  :url "http://github.com/clojure-grimoire/lib-grimoire"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [version-clj "0.1.0"
                  :exclusions [org.clojure/clojure]]
                 [me.arrdem/guten-tag "0.1.0"
                  :exclusions [org.clojure/clojure]]
                 [org.clojure/core.match "0.3.0-alpha4"
                  :exclusions [org.clojure/clojure]]
                 [com.cemerick/url "0.1.1"
                  :exclusions [com.cemerick/clojurescript.test
                               org.clojure/clojure]]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.6.1"
                                   :exclusions [org.clojure/clojure]]]}})
