(ns grimoire.api.fs2.impl
  "Filesystem v2 datastore implementation details.

  This namespace is not part of
  the intentional API exposed in `grimoire.api` and should not be used by
  library client code.

  The desired directory tree is as follows:

  ```
  /<root>
   /<group>
    /meta.edn
    /attachments
      /notes
      /examples
      /related.edn
    /children/
      /<artifact>
        /meta.edn
        /attachments
          /notes
          /examples
          /related.edn
        /children
          /<version>
  ...
  ```

  The intent of this structure is to disambiguate between the
  children (Things) under a Thing, and the notes/examples/metadata
  attached to that particular node. This allows us to retain the
  original file system backend's simplicity in listing and adding to
  the store, while generalizing the attachment of structures such as
  notes and examples to nodes in the hierarchy." 
  (:require [clojure.java.io :as io]
            [clojure.core.match :refer [match]] 
            [grimoire.util :as util :refer [file?]]
            [grimoire.things :as t] 
            [grimoire.api.fs2 :refer [Config?]]
            [guten-tag.core :refer [tag]]))

(defn thing->handle
  [{:keys [root] :as config} thing]
  {:pre [(Config? config)
         (t/thing? thing)]}
  (let [parent-thing (if (t/leaf? thing)
                       (:parent thing)
                       thing)
        components   (->> (iterate :parent parent-thing)
                          (take-while (complement nil?))
                          (map :name)
                          reverse
                          (interpose "children"))]
    (apply io/file root components)))

(defn thing->children [config thing]
  {:pre [(not (t/leaf? thing))]}
  (io/file (thing->handle config thing) "children"))

(defn thing->meta-handle [config thing]
  (io/file (thing->handle config) "meta.edn"))

(defn thing->related-handle [config thing]
  (io/file (thing->children config (:parent thing)) "related.edn"))

(defn thing->examples-handle [config thing]
  (io/file (thing->children config thing) "examples"))

(defn thing->notes-handle [config thing]
  (io/file (thing->children config thing) "notes"))
