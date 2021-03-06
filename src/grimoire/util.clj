(ns grimoire.util
  "A namespace of stupid little helper bits including the Grimoire munge operations."
  (:refer-clojure :exclude [munge])
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [cemerick.url :refer [url-encode url-decode]]))

(def ^:private
  munge-map
  "A subset of URL encoding for... shitty reasons"
  {
   "." "_DOT_"
   "/" "_SLASH_"
   "+" "%2B"
   "?" "%3F"
   "!" "%21"
   "&" "%26"
   "#" "%23"
   ":" "%3A"
   "<" "%3C"
   "=" "%3D"
   ">" "%3E"
   })

(defn munge
  "This is the munge function as used by the current version of Grimoire. Should only be applied to
  symbols. Namespaces, packages, groups and soforth need not be name munged.

  Note that this is _NOT_ a full isomorphism, since it lacks an escape."
  [s]
  (as-> s s
    (reduce (fn [s [c r]]
              (str/replace s c r))
            s munge-map)))

(defn unmunge
  "Inverts #'munge.

  Note that this is _NOT_ a full isomorphism, since it lacks an escape."
  [s]
  (as-> s s
    (str/replace s #"%2E" ".")
    (reduce (fn [s [c r]]
              (str/replace s r c))
            s munge-map)))

(defn url-munge
  "This is the old implementation of #'munge

  It's just url-encoding augmented with a specification illegal but decoder supported mapping of
  . -> %2E, which is retained in the codebase for compatability reasons with the existing file
  store."
  [s]
  (as-> s s
    (url-encode s)
    (str/replace s "." "%2E")))

(defn update-munge
  "This function attempts to transform the legacy munging of names
  into the modern munging above. Note that update-munge is _not_ an
  unmunge operation."
  [s]
  (-> s
      ;; Note: This list used to be longer, but the other
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
   "oxlang"  "ox"
   "pixie"   "pixi"
   "toccata" "toc"})

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
  (let [to-long (fn [^String s] (Long/parseLong s))
      [v q] (str/split version-str #"-" 2)
      [major minor incremental] (mapv to-long (str/split v #"\."))]
  [major
   (or minor 0)
   (or incremental 0)
   ;; qual1 will be one of "alpha" "beta" "rc", or "zfinal" if
   ;; version-str is of the form "x.y.z".  It will always be
   ;; lower case so that normal alphabetic order comparison is
   ;; used, without weirdness of upper case letters being sorted
   ;; earlier than lower case letters.
   (if q (str/lower-case (re-find #"[^\d]+" q)) "zfinal")
   (some->> q (re-find #"\d+$") to-long)]))

(defn edn-read-string-with-readers
  "Read a string with clojure.edn/read-string and additional reader functions
  installed.

  Currently the only additional reader function is the default reader function,
  which will return a tuple [:cant-read <tag symbol> <raw value>]."
  [s]
  (edn/read-string {:default (fn [t v] [:cant-read t v])}
                   s))
