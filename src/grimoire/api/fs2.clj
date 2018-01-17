(ns grimoire.api.fs2
  "Filesystem v2 datastore.

  Unlike the v1 file system back end which hard codes various
  expectations about the number, structure and location of certain
  Things, the v2 file system back end is designed to provide more
  general applicability in the face of clients such as clj-doc and
  stacks.

  Specifically, notes and examples are fully supported in one-to-many
  relationships with every Thing in the hierarchy."
  (:require [grimoire.util :refer [file?]]
            [guten-tag.core :refer [deftag]]))

(deftag Config
  "A configuration for the filesystem v2 back-end.

  Stores base paths for docs, notes and the examples."
  [root]
  {:pre [(file? root)]})

;; Load implementation details
(load "fs2/impl")
(load "fs2/read")
(load "fs2/write")
