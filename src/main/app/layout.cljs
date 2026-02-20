(ns app.layout
  "Central layout constants for the Phylo tree viewer.

  This namespace contains only the `LAYOUT` map, which defines pixel
  spacing, padding, marker sizes, and other constants used by both
  the rendering components and the tree layout algorithms.")

(def LAYOUT
  "Central layout constants controlling alignment and spacing (in pixels).

  Keys:
  - `:svg-padding-x`       - horizontal padding inside the SVG container
  - `:svg-padding-y`       - total vertical space above the tree y=0 origin.
                             Must be ≥ abs(scale-bar-line-y) + ~30 to fit the
                             scale bar labels and the reference-node label.
  - `:scale-bar-line-y`    - y-coordinate of the scale bar baseline within the
                             padded SVG group (negative = above tree y=0).
                             Derived values used by ScaleBar:
                               minor tick top:  scale-bar-line-y - 2
                               major tick top:  scale-bar-line-y - 4
                               tick label base: scale-bar-line-y - 8
                             Reference-node label is centered at
                               scale-bar-line-y / 2  (midpoint to tree).
  - `:header-height`       - height of the metadata column header bar
  - `:label-buffer`        - space reserved for tree tip labels
  - `:metadata-gap`        - gap between tip labels and metadata columns
  - `:default-col-width`   - default pixel width for metadata columns
  - `:toolbar-gap`         - spacing between toolbar controls
  - `:node-marker-radius`  - radius of circular node markers
  - `:node-marker-fill`    - fill color for node markers"
  {:svg-padding-x 40
   :svg-padding-y 56
   :scale-bar-line-y -36
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
