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
            [app.components.metadata :refer [StickyHeader MetadataTable]]
            [app.components.scale :as scale]
            [app.components.toolbar :refer [Toolbar]]
            [app.components.grid :refer [MetadataGrid]]
            [app.components.resizable-panel :refer [ResizablePanel]]
            [app.components.selection-bar :refer [SelectionBar]]
            [clojure.string :as str]))

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
  "Renders a solid scale bar with tick marks and labels."
  [{:keys [max-depth x-scale scale-origin]}]
  (let [{:keys [major-ticks minor-ticks]} (scale/scale-ticks {:max-depth max-depth
                                                              :x-scale x-scale})]
    ($ :g
       ($ :line {:x1 0 :y1 -18
                 :x2 (* max-depth x-scale) :y2 -18
                 :stroke "#111" :stroke-width 1})
       (for [t minor-ticks]
         ($ :line {:key (str "scale-minor-" t)
                   :x1 (* t x-scale) :y1 -20
                   :x2 (* t x-scale) :y2 -18
                   :stroke "#111" :stroke-width 1}))
       (for [t major-ticks]
         ($ :g {:key (str "scale-tick-" t)}
            ($ :line {:x1 (* t x-scale) :y1 -22
                      :x2 (* t x-scale) :y2 -18
                      :stroke "#111" :stroke-width 1})
            ($ :text {:x (* t x-scale) :y -26
                      :text-anchor "middle"
                      :style {:font-family "monospace"
                              :font-size "10px"
                              :fill "#111"}}
               (.toFixed (js/Number (scale/label-value scale-origin max-depth t)) 1)))))))

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
    (let [ticks [0]]
      ($ :g
         (for [t ticks]
           ($ :line {:key (str "grid-" t)
                     :x1 (* t x-scale) :y1 0
                     :x2 (* t x-scale) :y2 tree-height
                     :stroke "#eee"
                     :stroke-dasharray "4 4"
                     :stroke-width 1}))))))

(defn- asset-src
  "Returns a data URL for bundled assets when present, falling back to the path."
  [path]
  (let [assets (.-__PHYLO_ASSET_MAP__ js/window)]
    (or (and assets (aget assets path)) path)))

;; ---- Helpers for SVG coordinate conversion ----

(defn- client->svg
  "Convert client (screen) coordinates to SVG user-space coordinates.
  Returns [svg-x svg-y] or nil if the SVG's CTM is unavailable."
  [^js svg client-x client-y]
  (when-let [^js ctm (.getScreenCTM svg)]
    (let [^js pt (.matrixTransform
                  (js/DOMPoint. client-x client-y)
                  (.inverse ctm))]
      [(.-x pt) (.-y pt)])))

(def ^:private drag-threshold
  "Minimum manhattan-distance (px) before a mousedown→move is treated as
  a box-select rather than an accidental click."
  5)

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
  - `:scale-origin`            - `:tips` or `:root` for scale labeling
  - `:show-branch-lengths`     - whether to show internal node branch lengths
  - `:show-pixel-grid`         - whether to show pixel coordinate debug grid
  - `:col-spacing`             - extra horizontal spacing between metadata columns
  - `:width-px`                - total available width in pixels
  - `:component-height-px`     - total available height in pixels
  - `:highlights`              - map of {leaf-name -> color} for persistent highlights
  - `:selected-ids`            - set of leaf names currently selected in the grid"
  [{:keys [tree tips max-depth active-cols x-mult y-mult
           show-internal-markers show-branch-lengths scale-origin width-px component-height-px
           show-scale-gridlines show-pixel-grid col-spacing
           highlights selected-ids metadata-rows
           set-active-cols! set-selected-ids! set-metadata-rows!]}]
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
        svg-height      (+ tree-height 100)

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

        ;; ---- Box (lasso) selection state ----
        svg-ref                (uix/use-ref nil)
        [drag-rect set-drag-rect!] (uix/use-state nil) ;; {:x1 :y1 :x2 :y2} during drag

        handle-svg-mousedown
        (fn [^js e]
          (when (and (zero? (.-button e))
                     ;; Don't hijack clicks on interactive leaf elements
                     (not (#{"circle" "text"} (.-tagName (.-target e)))))
            (when-let [^js svg @svg-ref]
              (when-let [[sx sy] (client->svg svg (.-clientX e) (.-clientY e))]
                (let [shift?  (.-shiftKey e)
                      on-move (fn [^js me]
                                (when-let [[mx my] (client->svg svg (.-clientX me) (.-clientY me))]
                                  (set-drag-rect! {:x1 sx :y1 sy :x2 mx :y2 my})))
                      on-up   (fn on-up-fn [^js ue]
                                (when-let [[ex ey] (client->svg svg (.-clientX ue) (.-clientY ue))]
                                  (let [dx (- ex sx)
                                        dy (- ey sy)]
                                    (when (> (+ (js/Math.abs dx) (js/Math.abs dy)) drag-threshold)
                                      (let [min-x  (min sx ex) max-x (max sx ex)
                                            min-y  (min sy ey) max-y (max sy ey)
                                            pad-x  (:svg-padding-x LAYOUT)
                                            pad-y  (:svg-padding-y LAYOUT)
                                            hit-ids (into #{}
                                                          (comp
                                                           (filter (fn [tip]
                                                                     (let [lx (+ pad-x (* (:x tip) current-x-scale))
                                                                           ly (+ pad-y (* (:y tip) y-mult))]
                                                                       (and (<= min-x lx max-x)
                                                                            (<= min-y ly max-y)))))
                                                           (map :name))
                                                          tips)]
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
                               "100vh")
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
       ($ :div {:style {:flex "1" :overflow "auto" :position "relative" :border-bottom "2px solid #dee2e6"}}
          (when (seq active-cols)
            ($ StickyHeader {:columns active-cols
                             :start-offset metadata-start-x
                             :col-spacing col-spacing
                             :max-depth max-depth
                             :x-scale current-x-scale
                             :scale-origin scale-origin}))

          ($ :svg {:id "phylo-svg"
                   :ref svg-ref
                   :width svg-width
                   :height svg-height
                   :on-mouse-down handle-svg-mousedown
                   :style {:cursor (when drag-rect "crosshair")}}
             ;; Scale bar
             ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
                ($ ScaleBar {:max-depth max-depth
                             :x-scale current-x-scale
                             :scale-origin scale-origin}))

             ;; Debugging pixel grid
             (when show-pixel-grid
               ($ PixelGrid {:width svg-width :height svg-height :spacing 50}))

             ;; Scale gridlines
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
                                  :show-branch-lengths show-branch-lengths
                                  :scale-origin scale-origin
                                  :max-depth max-depth
                                  :marker-radius (:node-marker-radius LAYOUT)
                                  :marker-fill (:node-marker-fill LAYOUT)
                                  :highlights highlights
                                  :selected-ids selected-ids
                                  :on-toggle-selection toggle-selection})

             ;; Metadata columns
             (when (seq active-cols)
               ($ MetadataTable {:active-cols active-cols
                                 :tips tips
                                 :start-offset metadata-start-x
                                 :y-scale y-mult
                                 :col-spacing col-spacing}))

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
                           :pointer-events "none"})))))

       ;; Selection bar (above the grid)
       (when (seq active-cols)
         ($ SelectionBar))

       ;; Metadata grid (AG-Grid) in resizable bottom panel
       (when (seq active-cols)
         ($ ResizablePanel {:initial-height 250
                            :min-height 0
                            :max-height 1200}
            ($ MetadataGrid {:metadata-rows metadata-rows
                             :active-cols active-cols
                             :tips tips
                             :selected-ids selected-ids
                             :on-cols-reordered set-active-cols!
                             :on-selection-changed set-selected-ids!
                             :on-cell-edited handle-cell-edited}))))))

(defui EmptyState
  "Placeholder shown when no tree is loaded.
  Displays a centered message with the app header and toolbar."
  [{:keys [component-height-px]}]
  ;; TODO: eliminate redundancy between this component and the normal UI
  ($ :div {:style {:display "flex"
                   :flex-direction "column"
                   :height (if component-height-px
                             (str component-height-px "px")
                             "100vh")
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
        ($ :img {:src (asset-src "images/logo.svg") :height "32px"}))
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

(defui TreeContainer
  "Intermediate component that bridges state context and pure rendering.

  Reads raw state from context via [[state/use-app-state]], derives
  the positioned tree via [[tree/prepare-tree]] (memoized), and passes
  everything as props to [[TreeViewer]].
  When no Newick string is loaded, renders [[EmptyState]] instead."
  [{:keys [width-px component-height-px]}]
  (let [{:keys [newick-str metadata-rows active-cols
                x-mult y-mult show-internal-markers show-branch-lengths
                scale-origin show-scale-gridlines show-pixel-grid
                col-spacing highlights selected-ids
                set-active-cols! set-selected-ids! set-metadata-rows!]} (state/use-app-state)

        {:keys [tree tips max-depth]} (uix/use-memo
                                       (fn [] (when (and (string? newick-str)
                                                         (not (str/blank? newick-str)))
                                                (tree/prepare-tree newick-str metadata-rows active-cols)))
                                       [newick-str metadata-rows active-cols])]
    (if tree
      ($ TreeViewer {:tree tree
                     :tips tips
                     :max-depth max-depth
                     :active-cols active-cols
                     :x-mult x-mult
                     :y-mult y-mult
                     :show-internal-markers show-internal-markers
                     :show-branch-lengths show-branch-lengths
                     :scale-origin scale-origin
                     :show-scale-gridlines show-scale-gridlines
                     :show-pixel-grid show-pixel-grid
                     :col-spacing col-spacing
                     :highlights highlights
                     :selected-ids selected-ids
                     :width-px width-px
                     :metadata-rows metadata-rows
                     :set-active-cols! set-active-cols!
                     :set-selected-ids! set-selected-ids!
                     :set-metadata-rows! set-metadata-rows!
                     :component-height-px component-height-px})
      ($ EmptyState {:component-height-px component-height-px}))))
