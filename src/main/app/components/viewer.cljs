(ns app.components.viewer
  "Top-level viewer components that compose the tree visualization.

  Contains [[PixelGrid]], [[ScaleBar]], [[ScaleGridlines]], [[TreeViewer]],
  [[TreeContainer]], and [[EmptyState]]. TreeContainer reads state from
  React context and derives the positioned tree, passing everything as
  props to the pure TreeViewer."
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [defui $]]
            [app.specs :as specs]
            [app.state :as state]
            [app.layout :refer [LAYOUT compute-col-gaps]]
            [app.tree :as tree]
            [app.color :as color]
            [app.components.tree :refer [PhylogeneticTree]]
            [app.components.metadata :refer [StickyHeader MetadataTable]]
            [app.scale :as scale]
            [app.components.toolbar :refer [Toolbar]]
            [app.components.grid :refer [MetadataGrid]]
            [app.components.resizable-panel :refer [ResizablePanel]]
            [app.components.selection-bar :refer [SelectionBar]]
            [app.components.legend :refer [FloatingLegend legend-width]]
            [clojure.string :as str]
            [app.util :as util])
  (:require-macros [app.specs :refer [defui-with-spec]]))

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

(defui ScaleBar
  "Renders a solid scale bar with tick marks and labels.

  When `scale-units-label` is non-empty, a units label is rendered as a static
  `<text>` element just past the right end of the scale bar."
  [{:keys [max-depth x-scale scale-origin scale-units-label]}]
  (let [{:keys [major-ticks minor-ticks]} (scale/scale-ticks {:max-depth max-depth
                                                              :x-scale x-scale
                                                              :origin scale-origin})
        ;; All y-coords derived from the single centralised LAYOUT constant.
        bar-y   (:scale-bar-line-y LAYOUT)   ;; -18
        minor-y (- bar-y 2)                  ;; -20
        major-y (- bar-y 4)                  ;; -22
        label-y (- bar-y 8)]                 ;; -26
    ($ :g
       ($ :line {:x1 0 :y1 bar-y
                 :x2 (* max-depth x-scale) :y2 bar-y
                 :stroke "#111" :stroke-width 1})
       (for [t minor-ticks]
         ($ :line {:key (str "scale-minor-" t)
                   :x1 (* t x-scale) :y1 minor-y
                   :x2 (* t x-scale) :y2 bar-y
                   :stroke "#111" :stroke-width 1}))
       (for [t major-ticks]
         ($ :g {:key (str "scale-tick-" t)}
            ($ :line {:x1 (* t x-scale) :y1 major-y
                      :x2 (* t x-scale) :y2 bar-y
                      :stroke "#111" :stroke-width 1})
            ($ :text {:x (* t x-scale) :y label-y
                      :text-anchor "middle"
                      :style {:font-family "monospace"
                              :font-size "10px"
                              :fill "#111"}}
               (scale/format-label scale-origin max-depth t))))
       ;; Optional units label at the right end of the scale bar
       (when (not (str/blank? scale-units-label))
         ($ :text {:x (+ (* max-depth x-scale) 10)
                   :y label-y
                   :text-anchor "start"
                   :style {:font-family "monospace"
                           :font-size "10px"
                           :fill "#666"}}
            scale-units-label)))))

(s/def :app.specs/scale-gridlines-props
  (s/keys :req-un [:app.specs/max-depth
                   :app.specs/x-scale
                   :app.specs/tree-height
                   :app.specs/scale-origin]))

(defui ScaleGridlines*
  "Renders evolutionary-distance gridlines as dashed vertical SVG lines.

  Computes human-friendly tick intervals via [[scale/calculate-scale-unit]] and
  [[scale/get-ticks]], then draws one dashed line per tick across the full
  `tree-height`. Intended to be placed as a sibling of the tree and
  metadata in the SVG, inside the translated coordinate group.

  Props (see `::app.specs/scale-gridlines-props`):
  - `:max-depth`    - maximum x-coordinate in the tree
  - `:x-scale`      - horizontal scaling factor (pixels per branch-length unit)
  - `:tree-height`  - total height in pixels to span
  - `:scale-origin` - `:tips` or `:root` for tick placement"
  [{:keys [max-depth x-scale tree-height scale-origin]}]
  (let [{:keys [major-ticks]} (scale/scale-ticks {:max-depth max-depth
                                                  :x-scale x-scale
                                                  :origin scale-origin})
        ticks (or (seq major-ticks) [0])]
    ($ :g
       (for [t ticks]
         ($ :line {:key (str "grid-" t)
                   :x1 (* t x-scale) :y1 0
                   :x2 (* t x-scale) :y2 tree-height
                   :stroke "#eee"
                   :stroke-dasharray "4 4"
                   :stroke-width 1})))))

(defui-with-spec ScaleGridlines
  [{:spec :app.specs/scale-gridlines-props :props props}]
  ($ ScaleGridlines* props))
#_(def ScaleGridlines ScaleGridlines*)

(defn- asset-src
  "Returns a data URL for bundled assets when present, falling back to the path."
  [path]
  (let [assets (.-__PHYLO_ASSET_MAP__ js/window)]
    (or (and assets (aget assets path)) path)))

;; ---- Helpers for SVG coordinate conversion ----
(def ^:private drag-threshold
  "Minimum manhattan-distance (px) before a mousedown→move is treated as
  a box-select rather than an accidental click."
  5)

(s/def :app.specs/tree-viewer-props
  (s/keys :req-un [:app.specs/tree
                   :app.specs/tips
                   :app.specs/max-depth
                   :app.specs/x-mult :app.specs/y-mult
                   :app.specs/show-internal-markers
                   :app.specs/show-scale-gridlines
                   :app.specs/show-pixel-grid
                   :app.specs/show-distance-from-origin
                   :app.specs/scale-origin
                   :app.specs/col-spacing
                   :app.specs/left-shift-px
                   :app.specs/tree-metadata-gap-px
                   :app.specs/width-px
                   :app.specs/component-height-px
                   :app.specs/active-cols :app.specs/set-active-cols!
                   :app.specs/metadata-rows :app.specs/set-metadata-rows!
                   :app.specs/set-selected-ids!
                   :app.specs/metadata-panel-collapsed
                   :app.specs/metadata-panel-height
                   :app.specs/metadata-panel-last-drag-height
                   :app.specs/set-metadata-panel-height!
                   :app.specs/set-metadata-panel-last-drag-height!
                   :app.specs/legend-pos :app.specs/set-legend-pos!
                   :app.specs/legend-collapsed? :app.specs/set-legend-collapsed!
                   :app.specs/legend-labels :app.specs/set-legend-labels!
                   :app.specs/legend-visible? :app.specs/set-legend-visible!
                   :app.specs/set-left-shift-px!
                   :app.specs/set-tree-metadata-gap-px!]
          :opt-un [:app.specs/highlights :app.specs/selected-ids
                   :app.specs/color-by-enabled? :app.specs/color-by-field
                   :app.specs/color-by-palette
                   :app.specs/color-by-type-override
                   :app.specs/branch-length-mult
                   :app.specs/scale-units-label
                   :app.specs/node-distances
                   :app.specs/show-distance-from-node
                   :app.specs/reference-node-name]))

(defui TreeViewer*
  "Top-level visualization shell that combines toolbar, metadata header,
  and a scrollable SVG viewport containing the tree, gridlines, and
  metadata columns.

  Props (see `:app.specs/tree-viewer-props`):
  - `:tree`                    - positioned root node
  - `:tips`                    - flat vector of enriched leaf nodes
  - `:max-depth`               - maximum x-coordinate in the tree
  - `:active-cols`             - vector of column config maps
  - `:x-mult`                  - horizontal zoom multiplier
  - `:y-mult`                  - vertical tip spacing
  - `:show-internal-markers`   - whether to show circles on internal nodes
  - `:show-scale-gridlines`    - whether to show evolutionary distance gridlines
  - `:scale-origin`            - `:tips` or `:root` for scale labeling
  - `:show-distance-from-origin`     - whether to show internal node distances from origin
  - `:show-pixel-grid`         - whether to show pixel coordinate debug grid
  - `:col-spacing`             - extra horizontal spacing between metadata columns
  - `:width-px`                - total available width in pixels
  - `:component-height-px`     - total available height in pixels
  - `:highlights`              - map of {leaf-name -> color} for persistent highlights
  - `:color-by-enabled?`       - whether metadata color-by is active
  - `:color-by-field`          - keyword for the metadata field to color by
  - `:color-by-palette`        - palette id keyword for auto-coloring
  - `:color-by-type-override` - type override (:auto, :categorical, :numeric, :date)
  - `:selected-ids`            - set of leaf names currently selected in the grid
  - `:metadata-panel-collapsed` - whether the metadata grid panel is collapsed
  - `:metadata-panel-height`    - current height of the metadata grid panel
  - `:metadata-panel-last-drag-height` - last height set by dragging
  - `:set-metadata-panel-height!` - setter for panel height
  - `:set-metadata-panel-last-drag-height!` - setter for last drag height"
  [{:keys [tree
           tips
           max-depth
           x-mult
           y-mult
           show-internal-markers
           show-distance-from-origin
           scale-origin
           width-px
           component-height-px
           show-scale-gridlines
           show-pixel-grid
           col-spacing
           highlights
           metadata-panel-collapsed
           color-by-enabled?
           color-by-field
           color-by-palette
           color-by-type-override
           active-cols           set-active-cols!
           selected-ids          set-selected-ids!
           metadata-rows         set-metadata-rows!
           metadata-panel-height set-metadata-panel-height!
           metadata-panel-last-drag-height set-metadata-panel-last-drag-height!
           tree-metadata-gap-px  set-tree-metadata-gap-px!
           left-shift-px         set-left-shift-px!
           legend-pos            set-legend-pos!
           legend-collapsed?     set-legend-collapsed!
           legend-labels         set-legend-labels!
           legend-visible?       set-legend-visible!
           active-reference-node-id set-active-reference-node-id!
           positioned-tree         set-positioned-tree!
           branch-length-mult scale-units-label
           node-distances
           show-distance-from-node
           reference-node-name]}]
  (let [;; Dynamic layout math
        current-x-scale (if (pos? max-depth)
                          (* (/ (- width-px 400) max-depth) x-mult)
                          1)
        ;; Branch-length multiplier for display: scales tick labels and distance
        ;; labels without affecting pixel positions.
        bl-mult           (or branch-length-mult 1)
        effective-max-depth (* max-depth bl-mult)
        effective-x-scale   (/ current-x-scale bl-mult)
        tree-end-x      (+ (* max-depth current-x-scale) (:label-buffer LAYOUT))
        metadata-start-x (+ (:svg-padding-x LAYOUT)
                            tree-end-x
                            (:metadata-gap LAYOUT)
                            (or tree-metadata-gap-px 0))
        left-shift      (or left-shift-px 0)
        tree-height     (* (count tips) y-mult)
        legend-right-pad 12
        legend-right-edge (when (and legend-pos (number? (:x legend-pos)))
                            (+ (:x legend-pos) legend-width legend-right-pad))
        col-gaps        (uix/use-memo #(compute-col-gaps active-cols col-spacing) [active-cols col-spacing])
        base-svg-width  (+ metadata-start-x
                           (reduce + 0 (map :width active-cols))
                           (reduce + 0 col-gaps)
                           (max 0 left-shift)
                           100)
        svg-width       (max base-svg-width (or legend-right-edge 0))
        svg-height      (+ tree-height 100)

        color-by-map (uix/use-memo
                      (fn []
                        (when (and color-by-enabled? color-by-field (seq tips))
                          (color/build-color-map tips color-by-field color-by-palette color-by-type-override)))
                      [color-by-enabled? color-by-field color-by-palette color-by-type-override tips])
        merged-highlights (uix/use-memo #(merge (or color-by-map {}) (or highlights {})) [color-by-map highlights])

        field-keys (into #{} (map :key) active-cols)
        legend-field (when (contains? field-keys color-by-field) color-by-field)
        field-label (when legend-field
                      (some (fn [{:keys [key label]}]
                              (when (= key legend-field) label))
                            active-cols))
        legend-auto (uix/use-memo
                     (fn []
                       (when (and color-by-enabled? legend-field (seq tips))
                         (color/build-legend tips legend-field color-by-palette color-by-type-override)))
                     [color-by-enabled? legend-field color-by-palette color-by-type-override tips])
        {:keys [sections]} (uix/use-memo
                            (fn []
                              (color/build-legend-sections legend-auto field-label highlights legend-labels))
                            [legend-auto field-label highlights legend-labels])
        legend-sections sections
        show-legend? (boolean legend-visible?)

        _auto-show-legend
        (uix/use-effect
         (fn []
           (when (and (not legend-visible?)
                      (seq legend-sections)
                      (nil? legend-pos))
             (set-legend-visible! true)))
         [legend-visible? legend-sections legend-pos set-legend-visible!])

        ;; Layout refs for sizing the metadata panel
        viewport-ref         (uix/use-ref nil)
        [panel-max-height set-panel-max-height!] (uix/use-state 250)

        ;; Refs to access latest values in the resize listener without changing callback identity
        metadata-panel-collapsed-ref (uix/use-ref metadata-panel-collapsed)
        metadata-panel-height-ref    (uix/use-ref metadata-panel-height)

        ;; Keep refs in sync with state
        _sync-collapsed-ref
        (uix/use-effect
         (fn [] (reset! metadata-panel-collapsed-ref metadata-panel-collapsed))
         [metadata-panel-collapsed])

        _sync-height-ref
        (uix/use-effect
         (fn [] (reset! metadata-panel-height-ref metadata-panel-height))
         [metadata-panel-height])

        ;; Toggle a single leaf in/out of selected-ids
        toggle-selection (uix/use-callback
                          (fn [leaf-name]
                            (set-selected-ids!
                             (fn [ids]
                               (let [ids (or ids #{})]
                                 (if (contains? ids leaf-name)
                                   (disj ids leaf-name)
                                   (conj ids leaf-name))))))
                          [set-selected-ids!])

        ;; Toggle subtree selection: if any selected, clear all; else select all
        select-subtree (uix/use-callback
                        (fn [node]
                          (let [leaf-names (:leaf-names node)]
                            (when (seq leaf-names)
                              (set-selected-ids!
                               (fn [ids]
                                 (let [ids (or ids #{})]
                                   (if (some ids leaf-names)
                                     (reduce disj ids leaf-names)
                                     (into ids leaf-names))))))))
                        [set-selected-ids!])

        ;; Update a single cell in metadata-rows when the grid is edited
        handle-cell-edited (uix/use-callback
                            (fn [id-value field-kw new-value]
                              (set-metadata-rows!
                               (mapv (fn [row]
                                       (if (= (get row (-> active-cols first :key)) id-value)
                                         (assoc row field-kw new-value)
                                         row))
                                     metadata-rows)))
                            [metadata-rows active-cols set-metadata-rows!])

        ;; Stable callback that reads latest values from refs
        update-panel-max-height
        (uix/use-callback
         (fn []
           (when-let [^js el @viewport-ref]
             (let [viewport-height (.-height (.getBoundingClientRect el))
                   sticky-height (:header-height LAYOUT)
                   collapsed? @metadata-panel-collapsed-ref
                   current-height @metadata-panel-height-ref
                   current-panel-height (if collapsed? 0 (or current-height 0))
                   next-max (max 0 (+ current-panel-height (- viewport-height sticky-height)))]
               (set-panel-max-height! next-max))))
         [])

        ;; Register resize listener once on mount
        _panel-max-effect
        (uix/use-effect
         (fn []
           (update-panel-max-height)
           (let [on-resize (fn [_] (update-panel-max-height))]
             (.addEventListener js/window "resize" on-resize)
             (fn []
               (.removeEventListener js/window "resize" on-resize))))
         [update-panel-max-height])

        ;; Re-calculate when active-cols changes (affects layout)
        _recalc-on-cols-change
        (uix/use-effect
         (fn [] (update-panel-max-height))
         [active-cols update-panel-max-height])

        ;; ---- Box (lasso) selection state ----
        svg-ref                (uix/use-ref nil)
        [drag-rect set-drag-rect!] (uix/use-state nil) ;; {:x1 :y1 :x2 :y2} during drag

        handle-svg-mousedown
        (fn [^js e]
          (when (and (zero? (.-button e))
                     ;; Don't hijack clicks on interactive leaf elements
                     (not (#{"circle" "text"} (.-tagName (.-target e)))))
            (when-let [^js svg @svg-ref]
              (when-let [[sx sy] (util/client->svg svg (.-clientX e) (.-clientY e))]
                (let [shift?  (.-shiftKey e)
                      on-move (fn [^js me]
                                (when-let [[mx my] (util/client->svg svg (.-clientX me) (.-clientY me))]
                                  (set-drag-rect! {:x1 sx :y1 sy :x2 mx :y2 my})))
                      on-up   (fn on-up-fn [^js ue]
                                (when-let [[ex ey] (util/client->svg svg (.-clientX ue) (.-clientY ue))]
                                  (let [dx (- ex sx)
                                        dy (- ey sy)]
                                    (when (> (+ (js/Math.abs dx) (js/Math.abs dy)) drag-threshold)
                                      (let [hit-ids (tree/leaves-in-rect
                                                     tips
                                                     {:min-x (min sx ex) :max-x (max sx ex)
                                                      :min-y (min sy ey) :max-y (max sy ey)}
                                                     current-x-scale y-mult
                                                     (:svg-padding-x LAYOUT) (:svg-padding-y LAYOUT)
                                                     left-shift)]
                                        (if shift?
                                          (set-selected-ids! (fn [ids] (into (or ids #{}) hit-ids)))
                                          (set-selected-ids! hit-ids))))))
                                (set-drag-rect! nil)
                                (.removeEventListener js/document "mousemove" on-move)
                                (.removeEventListener js/document "mouseup" on-up-fn))]
                  (.addEventListener js/document "mousemove" on-move)
                  (.addEventListener js/document "mouseup" on-up))))))]

    ($ :div {:style {:display "flex"
                     :flex-direction "column"
                     :height (if component-height-px
                               (str component-height-px "px")
                               "calc(100vh - var(--phylo-top-offset, 0px))")
                     :padding-bottom "20px"
                     :box-sizing "border-box"}}

       ;; Header
       ($ :header {:style {:height "24px"
                           :display "flex"
                           :align-items "center"
                           :justify-content "space-between"
                           :padding "0 20px"
                           :background "#ffffff"
                           :color "#003366"
                           :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
                           :flex-shrink "0"}}
          ($ :h1 {:style {:font-size "18px"
                          :font-weight 600
                          :margin 0
                          :letter-spacing "0.5px"}}
             "Phylo Viewer")
          #_($ :img {:src (asset-src "images/logo.svg") :height "32px"}))

;; Toolbar
       ($ Toolbar)

       ;; Scrollable viewport
       ($ :div {:ref viewport-ref
                :style {:flex "1" :overflow "auto" :position "relative" :border-bottom "2px solid #dee2e6"}}
          ($ StickyHeader {:columns active-cols
                           :start-offset metadata-start-x
                           :col-spacing col-spacing
                           :max-depth effective-max-depth
                           :x-scale effective-x-scale
                           :scale-origin scale-origin
                           :left-shift-px left-shift-px
                           :tree-metadata-gap-px tree-metadata-gap-px
                           :set-left-shift-px! set-left-shift-px!
                           :set-tree-metadata-gap-px! set-tree-metadata-gap-px!
                           :set-active-cols! set-active-cols!
                           :width svg-width})

          ($ :svg {:id "phylo-svg"
                   :ref svg-ref
                   :width svg-width
                   :height svg-height
                   :on-mouse-down handle-svg-mousedown
                   :style {:cursor (when drag-rect "crosshair")}}
             ($ :g {:transform (str "translate(" left-shift ", 0)")}
                ;; Scale bar + reference label, both inside the padded group so
                ;; they share the same x/y coordinate space.
                ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
                   ($ ScaleBar {:max-depth effective-max-depth
                                :x-scale effective-x-scale
                                :scale-origin scale-origin
                                :scale-units-label scale-units-label})
                   ;; Reference node context label — positioned at the midpoint
                   ;; between scale-bar-line-y and tree y=0, so it always sits
                   ;; in the gap between the bar line and the first leaf.
                   ;; Rendered in SVG so it appears in SVG and HTML exports.
                   (when (and show-distance-from-node active-reference-node-id)
                     ($ :text {:x 0
                               :y (/ (:scale-bar-line-y LAYOUT) 2)
                               :dominant-baseline "central"
                               :style {:font-family "monospace"
                                       :font-size "10px"
                                       :fill "#666"}}
                        (str "Leaf node distances from: "
                             (if (and reference-node-name (not (str/blank? reference-node-name)))
                               reference-node-name
                               "(internal node)")))))

;; Debugging pixel grid
                (when show-pixel-grid
                  ($ PixelGrid {:width svg-width :height svg-height :spacing 50}))

                ;; Scale gridlines
                (when show-scale-gridlines
                  ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
                     ($ ScaleGridlines {:max-depth effective-max-depth
                                        :x-scale effective-x-scale
                                        :tree-height tree-height
                                        :scale-origin scale-origin})))

                ;; The tree itself
                ($ PhylogeneticTree {:tree tree
                                     :x-scale current-x-scale
                                     :y-scale y-mult
                                     :show-internal-markers show-internal-markers
                                     :show-distance-from-origin show-distance-from-origin
                                     :scale-origin scale-origin
                                     :max-depth max-depth
                                     :branch-length-mult bl-mult
                                     :node-distances node-distances
                                     :marker-radius (:node-marker-radius LAYOUT)
                                     :marker-fill (:node-marker-fill LAYOUT)
                                     :highlights merged-highlights
                                     :selected-ids selected-ids
                                     :active-reference-node-id active-reference-node-id
                                     :set-active-reference-node-id! set-active-reference-node-id!
                                     :on-toggle-selection toggle-selection
                                     :on-select-subtree select-subtree})

                ;; Metadata columns
                (when (seq active-cols)
                  ($ MetadataTable {:active-cols active-cols
                                    :tips tips
                                    :start-offset metadata-start-x
                                    :y-scale y-mult
                                    :col-spacing col-spacing})))

             ;; Drag-select rectangle overlay
             (when drag-rect
               (let [{:keys [x1 y1 x2 y2]} drag-rect]
                 ($ :rect {:x (min x1 x2)
                           :y (min y1 y2)
                           :width  (js/Math.abs (- x2 x1))
                           :height (js/Math.abs (- y2 y1))
                           :fill "rgba(70, 130, 180, 0.15)"
                           :stroke "rgba(70, 130, 180, 0.6)"
                           :stroke-width 1
                           :stroke-dasharray "4 2"
                           :pointer-events "none"})))

             (when show-legend?
               ($ FloatingLegend {:svg-ref svg-ref
                                  :svg-width svg-width
                                  :svg-height svg-height
                                  :title "Legend"
                                  :sections legend-sections
                                  :position legend-pos
                                  :set-position! set-legend-pos!
                                  :collapsed? legend-collapsed?
                                  :set-collapsed! set-legend-collapsed!
                                  :labels legend-labels
                                  :set-labels! set-legend-labels!
                                  :on-close (fn [] (set-legend-visible! false))}))))

       ;; Selection bar (above the grid)
       ($ SelectionBar {:max-panel-height panel-max-height})

       ;; Metadata grid (AG-Grid) in resizable bottom panel
       (when (and (seq active-cols) (not metadata-panel-collapsed))
         ($ ResizablePanel {:height metadata-panel-height
                            :initial-height 250
                            :min-height 0
                            :max-height panel-max-height
                            :on-height-change (fn [new-h]
                                                (set-metadata-panel-height! new-h)
                                                (set-metadata-panel-last-drag-height! new-h))}
            ($ MetadataGrid {:metadata-rows metadata-rows
                             :active-cols active-cols
                             :tips tips
                             :selected-ids selected-ids
                             :on-cols-reordered set-active-cols!
                             :on-selection-changed set-selected-ids!
                             :on-cell-edited handle-cell-edited}))))))

(defui-with-spec TreeViewer
  [{:spec :app.specs/tree-viewer-props :props props}]
  ($ TreeViewer* props))
#_(def TreeViewer TreeViewer*)

(defui EmptyState
  "Placeholder shown when no tree is loaded.
  Displays a centered message with the app header and toolbar."
  [{:keys [component-height-px]}]
  ;; TODO: eliminate redundancy between this component and the normal UI
  ($ :div {:style {:display "flex"
                   :flex-direction "column"
                   :height (if component-height-px
                             (str component-height-px "px")
                             "calc(100vh - var(--phylo-top-offset, 0px))")
                   :box-sizing "border-box"}}
     ;; Header
     ($ :header {:style {:height "48px"
                         :display "flex"
                         :align-items "center"
                         :justify-content "space-between"
                         :padding "0 20px"
                         :background "#ffffff"
                         :color "#003366"
                         :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
                         :flex-shrink "0"}}
        ($ :h1 {:style {:font-size "18px"
                        :font-weight 600
                        :margin 0
                        :letter-spacing "0.5px"}}
           "Phylo Viewer")
        #_($ :img {:src (asset-src "images/logo.svg") :height "32px"}))
     ;; Toolbar
     ($ Toolbar)
     ;; Empty-state message
     ($ :div {:style {:flex "1"
                      :display "flex"
                      :flex-direction "column"
                      :align-items "center"
                      :justify-content "center"
                      :color "#8893a2"
                      :font-family "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"}}

        ;; TODO: factor this out into a component
        ($ :svg {:width 80 :height 80 :viewBox "0 0 24 24"
                 :fill "none" :stroke "#c0c8d4" :stroke-width 1.2
                 :stroke-linecap "round" :stroke-linejoin "round"
                 :style {:margin-bottom "16px"}}
           ;; Simple tree/branch icon
           ($ :line {:x1 12 :y1 20 :x2 12 :y2 10})
           ($ :line {:x1 12 :y1 10 :x2 6  :y2 4})
           ($ :line {:x1 12 :y1 10 :x2 18 :y2 4})
           ($ :line {:x1 6  :y1 4  :x2 3  :y2 1})
           ($ :line {:x1 6  :y1 4  :x2 9  :y2 1})
           ($ :line {:x1 18 :y1 4  :x2 15 :y2 1})
           ($ :line {:x1 18 :y1 4  :x2 21 :y2 1}))
        ($ :p {:style {:font-size "16px"
                       :font-weight 500
                       :margin "0 0 6px"
                       :color "#5a6577"}}
           "No tree loaded")
        ($ :p {:style {:font-size "13px"
                       :margin 0}}
           "Load a Newick file using the toolbar above."))))

(s/def :app.specs/tree-container-props
  (s/keys :req-un [:app.specs/width-px]
          :opt-un [:app.specs/component-height-px]))

(defui TreeContainer*
  "Intermediate component that bridges state context and pure rendering.

  Reads raw state from context via [[state/use-app-state]], derives
  the positioned tree via [[tree/parse-and-position]] or
  [[tree/position-tree]] (when a pre-parsed tree is available, e.g.
  from Nextstrain import), and enriches leaves via
  [[tree/enrich-leaves]]. Both stages are memoized separately so that
  metadata changes don't re-parse the Newick string.
  When no tree is available, renders [[EmptyState]] instead."
  [{:keys [width-px component-height-px]}]
  (let [{:keys [newick-str
                parsed-tree
                metadata-rows set-metadata-rows!
                active-cols set-active-cols!
                x-mult
                y-mult
                show-internal-markers
                show-distance-from-origin
                scale-origin
                show-scale-gridlines
                show-pixel-grid
                col-spacing
                left-shift-px set-left-shift-px!
                tree-metadata-gap-px set-tree-metadata-gap-px!
                highlights
                selected-ids set-selected-ids!
                metadata-panel-collapsed
                metadata-panel-height set-metadata-panel-height!
                metadata-panel-last-drag-height set-metadata-panel-last-drag-height!
                color-by-enabled?
                color-by-field
                color-by-palette
                color-by-type-override
                legend-pos set-legend-pos!
                legend-collapsed? set-legend-collapsed!
                legend-labels set-legend-labels!
                legend-visible? set-legend-visible!
                active-reference-node-id set-active-reference-node-id!
                positioned-tree set-positioned-tree!
                show-distance-from-node
                branch-length-mult
                scale-units-label]} (state/use-app-state)

        ;; Stage 1: parse + position — re-runs when newick-str or parsed-tree changes.
        ;; When parsed-tree is available (e.g. Nextstrain import), uses it directly
        ;; via position-tree, skipping the Newick parse step.
        {:keys [tree raw-tips max-depth]}
        (uix/use-memo
         (fn []
           (cond
             parsed-tree
             (let [{:keys [tree tips max-depth]} (tree/position-tree parsed-tree)]
               (set-positioned-tree! tree)  ;; <-- Save it
               {:tree tree :raw-tips tips :max-depth max-depth})

             (and (string? newick-str) (not (str/blank? newick-str)))
             (let [{:keys [tree tips max-depth]} (tree/parse-and-position newick-str)]
               (set-positioned-tree! tree)  ;; <-- Save it
               {:tree tree :raw-tips tips :max-depth max-depth})))
         [newick-str parsed-tree set-positioned-tree!])

        ;; Stage 2: enrich leaves with metadata — re-runs when metadata or cols change
        tips (uix/use-memo
              (fn [] (when raw-tips
                       (tree/enrich-leaves raw-tips metadata-rows active-cols)))
              [raw-tips metadata-rows active-cols])

        ;; Stage 3: pairwise distances from ctrl-clicked reference node to ALL leaf nodes.
        ;; Re-runs when the toggle, reference node, tree, tips, or multiplier changes.
        ;; Returns nil when the toggle is off or prerequisites are missing.
        node-distances
        (uix/use-memo
         (fn []
           (when (and show-distance-from-node
                      active-reference-node-id
                      positioned-tree
                      (seq tips))
             (let [bl-mult (or branch-length-mult 1)]
               (reduce (fn [acc {:keys [name id]}]
                         (let [d (tree/distance-between positioned-tree active-reference-node-id id)]
                           (if d (assoc acc name (* d bl-mult)) acc)))
                       {}
                       tips))))
         [show-distance-from-node active-reference-node-id positioned-tree tips branch-length-mult])

        ;; Name of the reference node for the context label (nil for unnamed internal nodes).
        reference-node-name
        (when (and show-distance-from-node active-reference-node-id positioned-tree)
          (-> (tree/find-path-to-node positioned-tree active-reference-node-id) last :name))]
    (if tree
      ($ TreeViewer {:tree tree
                     :tips tips
                     :max-depth max-depth
                     :active-cols active-cols
                     :x-mult x-mult
                     :y-mult y-mult
                     :show-internal-markers show-internal-markers
                     :show-distance-from-origin show-distance-from-origin
                     :scale-origin scale-origin
                     :show-scale-gridlines show-scale-gridlines
                     :show-pixel-grid show-pixel-grid
                     :col-spacing col-spacing
                     :left-shift-px left-shift-px
                     :tree-metadata-gap-px tree-metadata-gap-px
                     :set-left-shift-px! set-left-shift-px!
                     :set-tree-metadata-gap-px! set-tree-metadata-gap-px!
                     :highlights highlights
                     :color-by-enabled? color-by-enabled?
                     :color-by-field color-by-field
                     :color-by-palette color-by-palette
                     :color-by-type-override color-by-type-override
                     :legend-pos legend-pos
                     :legend-collapsed? legend-collapsed?
                     :legend-labels legend-labels
                     :legend-visible? legend-visible?
                     :set-legend-pos! set-legend-pos!
                     :set-legend-collapsed! set-legend-collapsed!
                     :set-legend-labels! set-legend-labels!
                     :set-legend-visible! set-legend-visible!
                     :active-reference-node-id active-reference-node-id
                     :set-active-reference-node-id! set-active-reference-node-id!
                     :selected-ids selected-ids
                     :metadata-rows metadata-rows
                     :metadata-panel-collapsed metadata-panel-collapsed
                     :metadata-panel-height metadata-panel-height
                     :metadata-panel-last-drag-height metadata-panel-last-drag-height
                     :set-metadata-panel-height! set-metadata-panel-height!
                     :set-metadata-panel-last-drag-height! set-metadata-panel-last-drag-height!
                     :width-px width-px
                     :set-active-cols! set-active-cols!
                     :set-selected-ids! set-selected-ids!
                     :set-metadata-rows! set-metadata-rows!
                     :component-height-px component-height-px
                     :branch-length-mult branch-length-mult
                     :scale-units-label scale-units-label
                     :node-distances node-distances
                     :show-distance-from-node show-distance-from-node
                     :reference-node-name reference-node-name})
      ($ EmptyState {:component-height-px component-height-px}))))

(defui-with-spec TreeContainer
  [{:spec :app.specs/tree-container-props :props props}]
  ($ TreeContainer* props))
#_(def TreeContainer TreeContainer*)