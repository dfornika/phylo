(ns app.layout
  "Central layout constants for the Phylo tree viewer.

  This namespace contains only the `LAYOUT` map, which defines pixel
  spacing, padding, marker sizes, and other constants used by both
  the rendering components and the tree layout algorithms.")

(def LAYOUT
  "Central layout constants controlling alignment and spacing (in pixels).

  Keys:
  - `:svg-padding-x`     - horizontal padding inside the SVG container
  - `:svg-padding-y`     - vertical padding inside the SVG container
  - `:header-height`     - height of the metadata column header bar
  - `:label-buffer`      - space reserved for tree tip labels
  - `:metadata-gap`      - gap between tip labels and metadata columns
  - `:default-col-width` - default pixel width for metadata columns
  - `:toolbar-gap`       - spacing between toolbar controls
  - `:node-marker-radius` - radius of circular node markers
  - `:node-marker-fill`   - fill color for node markers"
  {:svg-padding-x 40
   :svg-padding-y 40
   :header-height 36
   :label-buffer 150
   :metadata-gap 20
   :default-col-width 120
   :toolbar-gap 20
   :node-marker-radius 3
   :node-marker-fill "#333"})

;; ===== Derived Layout Helpers =====

(defn compute-col-gaps
  "Computes per-column gap widths by merging global `col-spacing` with
  each column's own `:spacing` value.

  Returns a vector of gap widths (one per column in `active-cols`)."
  [active-cols col-spacing]
  (mapv (fn [col] (+ (or col-spacing 0) (or (:spacing col) 0)))
        active-cols))
