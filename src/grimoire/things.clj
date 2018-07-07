(ns grimoire.things
  "
  This namespace implements a \"thing\" structure, approximating a URI, for
  uniquely naming and referencing entities in a Grimoire documentation
  store."
  (:refer-clojure :exclude [def namespace parents])
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.core.match :refer [match]] 
            [grimoire.util :as u]
            [guten-tag.core :as t]
            [cemerick.url :as url]))

(s/def ::name string?)
(s/def ::handle uri?)

(defmulti thing-spec :type)
(defmethod thing-spec :default [_]
  (constantly false))
(s/def ::thing (s/multi-spec thing-spec :thing))

;; Groups, Artifacts and Versions Maven style.
;;--------------------------------------------------------------------------------------------------
(s/def :grimoire.things.group/type #{::group})
(s/def ::group
  (s/keys :req-un [:grimoire.things.group/type
                   ::name]))
(defmethod thing-spec ::group [_] ::group)

(s/def :grimoire.things.artifact/type #{::artifact})
(s/def :grimoire.things.artifact/parent ::group)
(s/def ::artifact
  (s/keys :req-un [:grimoire.things.artifact/type
                   ::name
                   :grimoire.things.artifact/parent]))
(defmethod thing-spec ::artifact [_] ::artifact)

(s/def :grimoire.things.version/type #{::version})
(s/def :grimoire.things.version/parent ::artifact)
(s/def ::version
  (s/keys :req-un [:grimoire.things.version/type
                   ::name
                   :grimoire.things.version/parent]))
(defmethod thing-spec ::artifact [_] ::artifact)

(s/def :grimoire.things.version/type #{::version})
(s/def :grimoire.things.version/parent ::artifact)
(s/def ::version
  (s/keys :req-un [:grimoire.things.version/type
                   ::name
                   :grimoire.things.version/parent]))
(defmethod thing-spec ::version [_] ::version)

(defn thing? [t]
  (s/valid? ::thing t))

;; Clojure-like Platforms with Namespaces and Defs
;;--------------------------------------------------------------------------------------------------
(s/def :grimoire.things.platform/type #{::platform})
(s/def :grimoire.things.platform/parent ::version)
(defmulti platform-spec :name)
(s/def ::platform
  (s/keys :req-un [:grimoire.things.platform/type
                   :grimoire.things.platform/parent
                   ::name]))
(defmethod thing-spec ::platform [_] ::platform)

(s/def :grimoire.things.clj*.platform/name #{"clj" "cljc" "cljs"})
(s/def :grimoire.things.clj*/platform
  (s/and ::platform
         (s/keys :req-un [:grimoire.things.clj*.platform/name])))

(s/def :grimoire.things.clj*.namespace/type #{::namespace})
(s/def :grimoire.things.clj*.namespace/parent :grimoire.things.clj*/platform)
(s/def ::namespace
  (s/keys :req-un [:grimoire.things.clj*.namespace/type
                   ::name
                   :grimoire.things.clj*.namespace/parent]))
(defmethod thing-spec ::namespace [_] ::namespace)

(s/def :grimoire.things.clj*.def/type #{::def})
(s/def :grimoire.things.clj*.def/parent ::namespace)
(s/def ::def
  (s/keys :req-un [:grimoire.things.clj*.def/type
                   ::name
                   :grimoire.things.clj*.def/parent]))
(defmethod thing-spec ::def [_] ::def)

;; Java Platform with Packages, Classes, Methods and Fields
;;--------------------------------------------------------------------------------------------------
(s/def :grimoire.things.jvm.platform/name #{"jvm"})
(s/def :grimoire.things.jvm/platform
  (s/and ::platform
         (s/keys :req-un [:grimoire.things.platform.jvm/name])))

(s/def :grimoire.things.package/type #{::package})
(s/def :grimoire.things.package/parent :grimoire.things.jvm/platform)
(s/def ::package
  (s/keys :req-un [:grimoire.things.package/type
                   ::name
                   :grimoire.things.package/parent]))
(defmethod thing-spec ::package [_] ::package)

(s/def :grimoire.things.class/type #{::class})
(s/def :grimoire.things.class/parent ::package)
(s/def ::class
  (s/keys :req-un [:grimoire.things.class/type
                   ::name
                   :grimoire.things.class/parent]))
(defmethod thing-spec ::class [_] ::class)

(s/def :grimoire.things.method/type #{::method})
(s/def :grimoire.things.method/parent ::class)
(s/def ::method
  (s/keys :req-un [:grimoire.things.method/type
                   ::name
                   :grimoire.things.method/parent]))
(defmethod thing-spec ::method [_] ::method)

(s/def :grimoire.things.field/type #{::field})
(s/def :grimoire.things.field/parent ::class)
(s/def ::field
  (s/keys :req-un [:grimoire.things.field/type
                   ::name
                   :grimoire.things.field/parent]))
(defmethod thing-spec ::field [_] ::field)

;; Examples and Documents as annotations on things
;;--------------------------------------------------------------------------------------------------
;; FIXME (arrdem 1/22/2018):
;;
;;  The intent here is that annotations should be attachable to any "concrete" thing, being anythign
;;  which is not a document or an example. Previously there was a concept of a "leaf"
(s/def :grimoire.things.annotation/parent
  (s/and ::thing
         #(not (s/valid? % ::group)
               (s/valid? % ::artifact))))
(s/def :grimoire.things.annotation/title ::name)

(s/def :grimoire.things.example/type #{::example})
(s/def ::example
  (s/keys :req-un [:grimoire.things.example/type
                   :grimoire.things.annotation/title
                   ::handle]))

;; FIXME (arrdem 1/22/2018):
;;
;;  There are many kinds of documents, such as warnings, articles, editorialization and particularly
;;  replacement or alternative docstrings. The original Grimoire "1.0" `Note` type was intended to
;;  represent an arbitrary article but in reality was a singleton representing a replacement
;;  docstring. Sadly, the ability to interpret documents as alternatives to docstrings is still much
;;  needed and this structure doesn't give a way to indicate the intended interpretation.
;;
;;  Maybe that's purely a UI issue? Seems like a concern of the document author tho..
(s/def :grimoire.things.document/type #{::document})
(s/def ::document
  (s/keys :req-un [:grimoire.things.document/type
                   :grimoire.things.annotation/title
                   ::handle]))

;;

(defn path->thing
  "String to Thing transformer which builds a Thing tree by splitting on /. The
  resulting things are rooted on a Group as required by the definition of a
  Thing."
  [path]
  ;; FIXME
  nil)

(defn thing->url-path
  "Function from a Thing to a munged and URL safe Thing path"
  [t]
  {:pre [(thing? t)]}
  (match [t]
    [([::def   {:name n :parent p}] :seq)]
    ,,(str (thing->url-path p) "/" (u/munge n))

    [([::group {:name n}] :seq)]
    ,,n

    [([_       {:name n :parent p}] :seq)]
    ,,(str (thing->url-path p) "/" n)))

;; FIXME: this function could probably be a little more principled,
;; but so be it.
(defn url-path->thing
  "Function from a URL to a Thing. Complement of thing->url-path."
  [url]
  (let [path-elems (string/split url #"/")
        path-elems (if (<= 6 (count path-elems))
                     (concat
                      (take 5 path-elems)
                      [(url/url-decode (nth path-elems 5))]
                      (drop 6 path-elems))
                     path-elems)]
    (path->thing (string/join "/" path-elems))))

(defn thing->type-name
  [t]
  {:pre [(thing? t)]}
  (match [t]
    [([::group     {}] :seq)] "group"
    [([::artifact  {}] :seq)] "artifact"
    [([::version   {}] :seq)] "version"
    [([::platform  {}] :seq)] "platform"
    [([::namespace {}] :seq)] "namespace"
    [([::def       {}] :seq)] "def"
    [([::example   {}] :seq)] "ex"))

(defn thing->full-uri
  "Function from a Thing to a String representing a unique Thing naming URI.

  URIs have the same structure as thing->url-path but are prefixed by <t>:
  where <t> is the lower cased name of the type of the input Thing.

  For example, a Thing represeting org.clojure/clojure would give the full URI
  grim+artifact:org.clojure/clojure. A Thing representing org.clojure/clojure/1.6.0
  would likewise be grim+version:org.clojure/clojure/1.6.0 and soforth."
  [t]
  {:pre [(thing? t)]}
  (format "grim+%s:%s"
          (thing->type-name t)
          (thing->url-path t)))

(def full-uri-pattern
  #"(grim\+(group|artifact|version|platform|namespace|def)(\+(note|example))?):([^?&#]+)([?&#].*)?")

(defn full-uri->thing
  "Complement of thing->full-uri."
  [uri-string]
  {:pre [(string? uri-string)]}
  (let [[_ scheme type _ extension path
         :as groups] (re-find full-uri-pattern uri-string)]
    (assert groups "Failed to parse URI. No regex match!")
    (let [t (url-path->thing path)]
      (assert (= (thing->type-name t) type)
              "Failed to parse URI. Path didn't round trip to expected type!")
      t)))

(def short-string-pattern
  #"(\w{3,6})::([^\s/,;:\"'\[\]\(\)\s]+)(/([^,;:\"\[\]\(\)\s]+))?")

(defn thing->short-string
  "Function from a Thing to a String representing a mostly unique naming string.
  
  Unlike thing->full-uri, thing->short-string will discard exact artifact, group
  and verison information instead giving only a URI with respect to the
  platform, namespace and name of a Thing.

  For example, the Thing representing
  org.clojure/clojure/1.6.0/clj/clojure.core/+ would give the short string
  clj::clojure.core/+."
  [t]
  {:post [(re-find short-string-pattern %)]}
  (match [t]
    [([::namespace {:name   nn
                    :parent {:name pn}}]
      :seq)]
    ,,(format "%s::%s" pn nn)

    [([::def {:name   n
              :parent {:name   nn
                       :parent {:name pn}}}]
      :seq)]
    ,,(format "%s::%s/%s"
              pn nn n)))

;; short-string->thing to be defined in terms of a search for the latest version.
(defn parse-short-string
  "Function from a String as generated by thing->short-string to one of
  either [:def nil nil nil <ns-name> <def-name>] or [:ns nil nil nil
  <ns-name>]. The intention is that this function can be used to parse
  short-strings into structures for which Things can be looked up out of a
  datastore. Returns nil on failure to parse."
  [s]
  {:pre [(string? s)]}
  (let [[_s platform ns _ ?def :as match] (re-find short-string-pattern s)]
    (when match
      (if ?def
        [:def nil nil nil platform ns ?def]
        [:ns  nil nil nil platform ns]))))
