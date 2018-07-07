(defproject org.clojure-grimoire/lib-grimoire "_"
  :description "A shared library for Grimoire infrastructure"
  :url "http://github.com/clojure-grimoire/lib-grimoire"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[me.arrdem/lein-git-version "2.0.8"]]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [me.arrdem/guten-tag "0.1.6"
                  :exclusions [org.clojure/clojure]]
                 [org.clojure/core.match "0.3.0-alpha4"
                  :exclusions [org.clojure/clojure]]
                 [com.cemerick/url "0.1.1"
                  :exclusions [com.cemerick/clojurescript.test
                               org.clojure/clojure]]]

  :git-version
  {:status-to-version
   (fn [{:keys [tag version branch ahead ahead? dirty?] :as git}]
     (if (and tag (not ahead?) (not dirty?))
       (do (assert (re-find #"\d+\.\d+\.\d+" tag)
                   "Tag is assumed to be a raw SemVer version")
           tag)
       (if (and tag (or ahead? dirty?))
         (let [[_ prefix patch] (re-find #"(\d+\.\d+)\.(\d+)" tag)
               patch            (Long/parseLong patch)
               patch+           (inc patch)]
           (format "%s.%d-%s-SNAPSHOT" prefix patch+ branch))
         "0.1.0-SNAPSHOT")))}

  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"
                                   :exclusions [org.clojure/clojure]]]}})
