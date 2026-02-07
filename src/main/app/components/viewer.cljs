(ns app.components.viewer
  "Top-level viewer components that compose the tree visualization.

  Contains [[PixelGrid]], [[ScaleGridlines]], [[TreeViewer]], and
  [[TreeContainer]]. TreeContainer reads state from React context
  and derives the positioned tree, passing everything as props to
  the pure TreeViewer."
  (:require [uix.core :as uix :refer [defui $]]
            [app.state :as state]
            [app.layout :refer [LAYOUT]]
            [app.tree :as tree]
            [app.components.tree :refer [PhylogeneticTree]]
            [app.components.metadata :refer [MetadataHeader MetadataTable]]
            [app.components.toolbar :refer [Toolbar]]))

(defui PixelGrid
  "SVG debug grid showing pixel coordinates.

  Renders light dashed lines at regular intervals with axis labels,
  useful for development and layout troubleshooting. Rendered in
  raw SVG pixel space (not affected by tree transforms).

  Props:
  - `:width`   - SVG canvas width in pixels
  - `:height`  - SVG canvas height in pixels
  - `:spacing` - grid line spacing in pixels (default 50)"
  [{:keys [width height spacing]
    :or {spacing 50}}]
  (let [grid-color "rgb(115, 179, 243)"
        label-color "#8ab"
        v-lines (range 0 (inc width) spacing)
        h-lines (range 0 (inc height) spacing)]
    ($ :g {:class "pixel-grid"}
       ;; Vertical lines
       (for [x v-lines]
         ($ :line {:key (str "pgv-" x)
                   :x1 x :y1 0 :x2 x :y2 height
                   :stroke grid-color
                   :stroke-dasharray "2 4"
                   :stroke-width 0.5}))
       ;; Horizontal lines
       (for [y h-lines]
         ($ :line {:key (str "pgh-" y)
                   :x1 0 :y1 y :x2 width :y2 y
                   :stroke grid-color
                   :stroke-dasharray "2 4"
                   :stroke-width 0.5}))
       ;; X-axis labels (top edge)
       (for [x v-lines :when (pos? x)]
         ($ :text {:key (str "pgxl-" x)
                   :x x :y 8
                   :text-anchor "middle"
                   :style {:font-family "monospace" :font-size "8px" :fill label-color}}
            (str x)))
       ;; Y-axis labels (left edge)
       (for [y h-lines :when (pos? y)]
         ($ :text {:key (str "pgyl-" y)
                   :x 2 :y (- y 2)
                   :style {:font-family "monospace" :font-size "8px" :fill label-color}}
            (str y))))))

(defui ScaleGridlines
  "Renders evolutionary-distance gridlines as dashed vertical SVG lines.

  Computes human-friendly tick intervals via [[tree/calculate-scale-unit]] and
  [[tree/get-ticks]], then draws one dashed line per tick across the full
  `tree-height`. Intended to be placed as a sibling of the tree and
  metadata in the SVG, inside the translated coordinate group.

  Props (see `::app.specs/scale-gridlines-props`):
  - `:max-depth`   - maximum x-coordinate in the tree
  - `:x-scale`     - horizontal scaling factor (pixels per branch-length unit)
  - `:tree-height` - total height in pixels to span"
  [{:keys [max-depth x-scale tree-height]}]
  (if (pos? max-depth)
    (let [unit  (tree/calculate-scale-unit (/ max-depth 5))
          ticks (tree/get-ticks max-depth unit)]
      ($ :g
         (for [t ticks]
           ($ :line {:key (str "grid-" t)
                     :x1 (* t x-scale) :y1 0
                     :x2 (* t x-scale) :y2 tree-height
                     :stroke "#eee"
                     :stroke-dasharray "4 4"
                     :stroke-width 1}))))
    ;; For non-positive max-depth, avoid calling `calculate-scale-unit`.
    ;; Render a single tick at 0 so the origin is still visible.
    (let [ticks [0]]
      ($ :g
         (for [t ticks]
           ($ :line {:key (str "grid-" t)
                     :x1 (* t x-scale) :y1 0
                     :x2 (* t x-scale) :y2 tree-height
                     :stroke "#eee"
                     :stroke-dasharray "4 4"
                     :stroke-width 1}))))))

(defui TreeViewer
  "Top-level visualization shell that combines toolbar, metadata header,
  and a scrollable SVG viewport containing the tree, gridlines, and
  metadata columns.

  Props (see `::app.specs/tree-viewer-props`):
  - `:tree`                    - positioned root node
  - `:tips`                    - flat vector of enriched leaf nodes
  - `:max-depth`               - maximum x-coordinate in the tree
  - `:active-cols`             - vector of column config maps
  - `:x-mult`                  - horizontal zoom multiplier
  - `:y-mult`                  - vertical tip spacing
  - `:show-internal-markers`   - whether to show circles on internal nodes
  - `:show-scale-gridlines`    - whether to show evolutionary distance gridlines
  - `:show-pixel-grid`         - whether to show pixel coordinate debug grid
  - `:col-spacing`             - extra horizontal spacing between metadata columns
  - `:width-px`                - total available width in pixels
  - `:component-height-px`     - total available height in pixels
  - `:highlight-set`           - (optional) set of leaf names to highlight
  - `:highlight-color`         - (optional) CSS color for highlighted markers"
  [{:keys [tree tips max-depth active-cols x-mult y-mult
           show-internal-markers width-px component-height-px
           show-scale-gridlines show-pixel-grid col-spacing
           highlight-set highlight-color]}]
  (let [;; Dynamic layout math
        current-x-scale (if (> max-depth 0)
                          (* (/ (- width-px 400) max-depth) x-mult)
                          1)
        tree-end-x      (+ (* max-depth current-x-scale) (:label-buffer LAYOUT))
        metadata-start-x (+ (:svg-padding-x LAYOUT)
                            tree-end-x
                            (:metadata-gap LAYOUT))
        tree-height     (* (count tips) y-mult)
        svg-width       (+ metadata-start-x
                           (reduce + 0 (map :width active-cols))
                           (* col-spacing (max 0 (dec (count active-cols))))
                           100)
        svg-height      (+ tree-height 100)]

    ($ :div {:style {:display "flex" :flex-direction "column" :height (str component-height-px "px")}}

       ;; Toolbar
       ($ Toolbar)

       ;; Scrollable viewport
       ($ :div {:style {:flex "1" :overflow "auto" :position "relative" :border-bottom "2px solid #dee2e6"}}
          (when (seq active-cols)
            ($ MetadataHeader {:columns active-cols :start-offset metadata-start-x}))

          ($ :svg {:id "phylo-svg" :width svg-width :height svg-height}
             ;; Debugging pixel grid
             (when show-pixel-grid
               ($ PixelGrid {:width svg-width :height svg-height :spacing 50}))

             ;; Scale gridlines (sibling — spans full SVG height)
             (when show-scale-gridlines
               ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
                  ($ ScaleGridlines {:max-depth max-depth
                                     :x-scale current-x-scale
                                     :tree-height tree-height})))

             ;; The tree itself
             ($ PhylogeneticTree {:tree tree
                                  :x-scale current-x-scale
                                  :y-scale y-mult
                                  :show-internal-markers show-internal-markers
                                  :marker-radius (:node-marker-radius LAYOUT)
                                  :marker-fill (:node-marker-fill LAYOUT)
                                  :highlight-set highlight-set
                                  :highlight-color highlight-color})

             ;; Metadata columns
             (when (seq active-cols)
               ($ MetadataTable {:active-cols active-cols
                                 :tips tips
                                 :start-offset metadata-start-x
                                 :y-scale y-mult
                                 :col-spacing col-spacing})))))))

(defui TreeContainer
  "Intermediate component that bridges state context and pure rendering.

  Reads raw state from context via [[state/use-app-state]], derives
  the positioned tree via [[tree/prepare-tree]] (memoized), computes the
  highlight set from date filter state, and passes everything as
  props to [[TreeViewer]]."
  [{:keys [width-px component-height-px]}]
  (let [{:keys [newick-str metadata-rows active-cols
                x-mult y-mult show-internal-markers
                show-scale-gridlines show-pixel-grid
                col-spacing
                date-filter-col date-filter-range
                highlight-color]} (state/use-app-state)

        {:keys [tree tips max-depth]} (uix/use-memo
                                       (fn [] (tree/prepare-tree newick-str metadata-rows active-cols))
                                       [newick-str metadata-rows active-cols])

        id-key (-> active-cols first :key)

        highlight-set (uix/use-memo
                       (fn [] (tree/compute-highlight-set metadata-rows id-key date-filter-col date-filter-range))
                       [metadata-rows id-key date-filter-col date-filter-range])]
    ($ TreeViewer {:tree tree
                   :tips tips
                   :max-depth max-depth
                   :active-cols active-cols
                   :x-mult x-mult
                   :y-mult y-mult
                   :show-internal-markers show-internal-markers
                   :show-scale-gridlines show-scale-gridlines
                   :show-pixel-grid show-pixel-grid
                   :col-spacing col-spacing
                   :highlight-set highlight-set
                   :highlight-color highlight-color
                   :width-px width-px
                   :component-height-px component-height-px})))
