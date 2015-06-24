(ns grimoire.api.fs
  (:require [guten-tag.core :refer [deftag]]))

(deftag Config
  "A configuration for the filesystem backend. Stores base paths for
  docs, notes and the examples."
  [docs
   notes
   examples]
  {:pre [(string? docs)
         (string? notes)
         (string? examples)]})
