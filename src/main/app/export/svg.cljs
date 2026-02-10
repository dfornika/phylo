(ns app.export.svg
  "Standalone SVG export helper."
  (:require [app.export.html :as export-html]))

(defn export-svg!
  "Exports the phylogenetic tree SVG to a file.

  Grabs the live `<svg>` DOM node by its id, clones it, adds the
  `xmlns` attribute required for standalone SVG files, serializes
  it to XML text, and triggers a save dialog."
  []
  (when-let [svg-el (js/document.getElementById "phylo-svg")]
    (let [clone      (.cloneNode svg-el true)
          _          (.setAttribute clone "xmlns" "http://www.w3.org/2000/svg")
          serializer (js/XMLSerializer.)
          svg-str    (.serializeToString serializer clone)
          blob       (js/Blob. #js [svg-str] #js {:type "image/svg+xml;charset=utf-8"})]
      (export-html/save-blob!
       blob
       "phylo-tree.svg"
       [{:description "SVG Image"
         :accept {"image/svg+xml" [".svg"]}}]))))
