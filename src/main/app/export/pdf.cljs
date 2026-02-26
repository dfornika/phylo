(ns app.export.pdf
  "PDF export using jsPDF and svg2pdf.js."
  (:require ["jspdf" :refer [jsPDF]]
            ["svg2pdf.js"]
            [app.io :as io]))

(defn export-pdf!
  "Export the current phylogenetic tree SVG as a PDF file.

  Reads the SVG element with id `phylo-svg`, determines its pixel
  dimensions, creates a jsPDF document sized to fit the SVG exactly,
  renders the SVG into the document using svg2pdf.js, and saves the
  result as `phylo-tree.pdf`."
  []
  (when-let [svg-node (js/document.getElementById "phylo-svg")]
    (let [w           (.. svg-node -width -baseVal -value)
          h           (.. svg-node -height -baseVal -value)
          orientation (if (> w h) "landscape" "portrait")
          doc         (new jsPDF #js {:orientation orientation
                                      :unit         "px"
                                      :format       #js [w h]})]
      (-> (.svg doc svg-node #js {:x 0 :y 0 :width w :height h})
          (.then (fn [] (.save doc "phylo-tree.pdf")))
          (.catch (fn [err] (js/console.error "PDF export failed:" err)))))))