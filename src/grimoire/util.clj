(ns grimoire.util
  "A namespace of stupid little helper bits including tye Grimoire munge
  operations."
  (:refer-clojure :exclude [munge])
  (:require [clojure.string :as str]
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
      (str/replace #"_?SLASH_?" "/")
      url-encode))

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
