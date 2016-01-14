(ns grimoire.util
  "A namespace of stupid little helper bits including tye Grimoire munge
  operations."
  (:refer-clojure :exclude [munge])
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [cemerick.url :refer [url-encode]]))

(defn munge
  "This is the munge function as used by the current version of
  Grimoire. Should only be applied to symbols. Namespaces, packages,
  groups and soforth need not be name munged."
  [s]
  (-> s
      (url-encode)
      (str/replace #"\." "%2E") ;; most applications don't eat the . character happily
      ))

(defn update-munge
  "This function attempts to transform the legacy munging of names
  into the modern munging above. Note that update-munge is _not_ an
  unmunge operation."
  [s]
  (-> s
      (str/replace #"_?DASH_?"  "-")
      (str/replace #"_?BANG_?"  "!")
      (str/replace #"_?STAR_?"  "*")
      (str/replace #"_?EQ_?"    "=")
      (str/replace #"_?LT_?"    "<")
      (str/replace #"_?GT_?"    ">")
      (str/replace #"_?QMARK_?" "?")
      (str/replace #"_?DOT_?"   ".")
      (str/replace #"_?SLASH_?" "/")))

;; FIXME: this should really be handled in data generation not in data use
(defn normalize-version [x]
  (if-not (re-matches #"[0-9]+.[0-9]+.[0-9]+" x)
    (str x ".0")
    x))

(def -abbrevs
  {"clojure"       "clj"
   "clojurescript" "cljs"
   "clojureclr"    "cljclr"

   ;; In the hope that one day support will be in order
   "oxlang"        "ox"
   "pixie"         "pixi"
   "toccata"       "toc"})

(def normalize-platform
  (comp #(get -abbrevs %1 %1)
        #(.toLowerCase %)
        name))

(defn clojure-version->cmp-key
  "Create a comparison key from a Clojure version string, as returned
  from function clojure.core/clojure-version.  The key is a vector of
  integers and strings, and two such keys may be compared via
  clojure.core/compare to get the correct relative order of the two
  Clojure versions.

  Makes simplifying assumption that:
  x.y.z-alpha<n> < x.y.z-beta<m> < x.y.z-RC<p> < x.y.z.

  Note that this was not the time order of release for some 1.5.0-beta
  and 1.5.0-RC releases, historically, as they switched from 1.5.0-RC
  releases back to 1.5.0-beta, and then back to 1.5.0-RC again.
  Hopefully that will not happen again.

  Stolen from AndyF with thanks."
  [version-str]
  (let [to-long         (fn [^String s] (Long/parseLong s))
        [major minor x] (str/split version-str #"\." -1)
        [incremental x] (str/split x #"-" -1)
        ;; qual1 will be one of "alpha" "beta" "rc", or "zfinal" if
        ;; version-str is of the form "x.y.z".  It will always be
        ;; lower case so that normal alphabetic order comparison is
        ;; used, without weirdness of upper case letters being sorted
        ;; earlier than lower case letters.
        [qual1 qual2]   (if x
                          (if-let [[_ a b] (re-matches #"^(?i)(alpha|beta|rc)(\d+)$" x)]
                            [(str/lower-case a) (to-long b)]
                            [x nil])
                          ["zfinal" nil])]
    [(to-long major) (to-long minor) (to-long incremental) qual1 qual2]))

(defn edn-read-string-with-readers
  "Read a string with clojure.edn/read-string and additional reader functions
  installed.

  Currently the only additional reader function is the default reader function,
  which will return a tuple [:cant-read <tag symbol> <raw value>]."
  [s]
  (edn/read-string {:default (fn [t v] [:cant-read t v])}
                   s))
