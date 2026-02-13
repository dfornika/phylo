(ns app.components.metadata
  "SVG rendering components for metadata column overlays.

  Contains [[StickyHeader]], [[MetadataColumn]], and [[MetadataTable]] —
  the components that render CSV/TSV metadata alongside the phylogenetic
  tree. All data arrives via props; these components do not access
  React context."
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [defui $]]
            [app.layout :refer [LAYOUT]]
            [app.components.scale :as scale]
            [app.specs :as specs])
  (:require-macros [app.specs :refer [defui-with-spec]]))




(def ^:private left-shift-min
  "Minimum allowed left shift (px)."
  -200)

(def ^:private left-shift-max
  "Maximum allowed left shift (px)."
  200)

(def ^:private shift-handle-width
  "Width of the left-shift drag handle in pixels."
  2)

(def ^:private col-spacing-min
  "Minimum per-column spacing adjustment (px)."
  0)

(def ^:private col-spacing-max
  "Maximum per-column spacing adjustment (px)."
  200)

(def ^:private col-handle-inset
  "Inset for per-column spacer handles (px)."
  4)

(def ^:private gap-handle-width
  "Width of the tree-metadata gap handle (px)."
  2)

(def ^:private gap-min
  "Minimum tree-metadata gap (px)."
  -100)

(def ^:private gap-max
  "Maximum tree-metadata gap (px)."
  200)

(s/def :app.specs/sticky-header-props
  (s/keys :req-un [:app.specs/columns
                   :app.specs/start-offset
                   :app.specs/max-depth
                   :app.specs/x-scale
                   :app.specs/scale-origin
                   :app.specs/left-shift-px
                   :app.specs/tree-metadata-gap-px
                   :app.specs/set-left-shift-px!
                   :app.specs/set-tree-metadata-gap-px!
                   :app.specs/set-active-cols!]
          :opt-un [:app.specs/sticky-header-width]))

(defui StickyHeader*
  "Renders a sticky header row displaying metadata column labels.

  Props (see `:app.specs/sticky-header-props`):
  - `:columns`      - seq of column config maps with `:key`, `:label`, `:width`
  - `:start-offset` - pixel offset where metadata columns begin
  - `:col-spacing`  - extra horizontal gap between columns (default 0)
  - `:max-depth`    - maximum x-coordinate in the tree
  - `:x-scale`      - horizontal scaling factor (pixels per branch-length unit)
  - `:scale-origin` - `:tips` or `:root` for scale labeling
  - `:left-shift-px` - horizontal shift applied to tree + metadata overlay
  - `:set-left-shift-px!` - setter for left shift
  - `:tree-metadata-gap-px` - extra spacing between tree and metadata
  - `:set-tree-metadata-gap-px!` - setter for tree-metadata gap
  - `:set-active-cols!` - setter for column configs
  - `:width`       - optional width for the sticky header"
  [{:keys [columns start-offset col-spacing max-depth x-scale scale-origin width
           left-shift-px tree-metadata-gap-px set-left-shift-px! set-tree-metadata-gap-px! set-active-cols!]}]
  (let [scale-width (max 0 (* max-depth x-scale))
        {:keys [major-ticks minor-ticks]} (scale/scale-ticks {:max-depth max-depth
                                                              :x-scale x-scale
                                                              :origin scale-origin})
        left-shift (or left-shift-px 0)
        dragging-ref (uix/use-ref false)
        start-x-ref (uix/use-ref 0)
        start-shift-ref (uix/use-ref left-shift)
        col-dragging-ref (uix/use-ref nil)
        col-start-x-ref (uix/use-ref 0)
        col-start-spacing-ref (uix/use-ref 0)
        gap-dragging-ref (uix/use-ref false)
        gap-start-x-ref (uix/use-ref 0)
        gap-start-ref (uix/use-ref (or tree-metadata-gap-px 0))
        handle-offset -6
        min-shift (+ 4 (- (:svg-padding-x LAYOUT)) (/ shift-handle-width 2) (- handle-offset))
        clamp-shift (fn [v] (-> v (max min-shift) (min left-shift-max)))
        clamp-col-spacing (fn [v] (-> v (max col-spacing-min) (min col-spacing-max)))
        clamp-gap (fn [v] (-> v (max gap-min) (min gap-max)))
        handle-left (+ (:svg-padding-x LAYOUT) left-shift (- (/ shift-handle-width 2)) handle-offset)]


    

    (uix/use-effect
     (fn []
       (let [on-move (fn [e]
                       (cond
                         @dragging-ref
                         (do
                           (.preventDefault e)
                           (let [dx (- (.-clientX e) @start-x-ref)
                                 next-shift (clamp-shift (+ @start-shift-ref dx))]
                             (when set-left-shift-px!
                               (set-left-shift-px! next-shift))))

                         @gap-dragging-ref
                         (do
                           (.preventDefault e)
                           (let [dx (- (.-clientX e) @gap-start-x-ref)
                                 next-gap (clamp-gap (+ @gap-start-ref dx))]
                             (when set-tree-metadata-gap-px!
                               (set-tree-metadata-gap-px! next-gap))))

                         @col-dragging-ref
                         (do
                           (.preventDefault e)
                           (let [dx (- (.-clientX e) @col-start-x-ref)
                                 next-spacing (clamp-col-spacing (+ @col-start-spacing-ref dx))
                                 col-key @col-dragging-ref]
                             (when (and set-active-cols! col-key)
                               (set-active-cols!
                                (mapv (fn [col]
                                        (if (= (:key col) col-key)
                                          (assoc col :spacing next-spacing)
                                          col))
                                      (vec columns))))))))
             on-up (fn [_e]
                     (reset! dragging-ref false)
                     (reset! gap-dragging-ref false)
                     (reset! col-dragging-ref nil))]
         (.addEventListener js/document "mousemove" on-move)
         (.addEventListener js/document "mouseup" on-up)
         (fn []
           (.removeEventListener js/document "mousemove" on-move)
           (.removeEventListener js/document "mouseup" on-up))))
     [clamp-gap clamp-shift clamp-col-spacing set-left-shift-px! set-tree-metadata-gap-px! set-active-cols! columns])

    ($ :div {:style {:position "sticky"
                     :top 0
                     :z-index 10
                     :background "#f8f9fa"
                     :border-bottom "2px solid #dee2e6"
                     :height (str (:header-height LAYOUT) "px")
                     :display "flex"
                     :align-items "center"
                     :font-family "sans-serif"
                     :font-size "12px"
                     :font-weight "bold"
                     :width "100%"
                     :min-width (when width (str width "px"))
                     :overflow "hidden"}
              :data-left-shift left-shift}
       ($ :div {:title "Drag to shift tree + metadata"
                :style {:position "absolute"
                        :left (str handle-left "px")
                        :top "6px"
                        :height "24px"
                        :width (str shift-handle-width "px")
                        :background "#8aa4c8"
                        :border-radius "3px"
                        :cursor "col-resize"
                        :box-shadow "0 0 0 1px rgba(0,0,0,0.08)"
                        :z-index 2
                        :pointer-events "auto"}
                :on-mouse-down (fn [e]
                                 (.preventDefault e)
                                 (reset! dragging-ref true)
                                 (reset! start-x-ref (.-clientX e))
                                 (reset! start-shift-ref left-shift))})

       ($ :div {:style {:position "relative"
                        :transform (str "translateX(" left-shift "px)")
                        :display "flex"
                        :align-items "center"
                        :height "100%"
                        :padding-left (str (:svg-padding-x LAYOUT) "px")
                        :z-index 1}}
          ($ :svg {:style {:position "absolute"
                           :left (str (:svg-padding-x LAYOUT) "px")
                           :top "10px"
                           :height "16px"
                           :width (str scale-width "px")
                           :pointer-events "none"}}
             ($ :line {:x1 0 :y1 12
                       :x2 (* max-depth x-scale) :y2 12
                       :stroke "#111" :stroke-width 1})
             (for [t minor-ticks]
               ($ :line {:key (str "hdr-minor-" t)
                         :x1 (* t x-scale) :y1 10
                         :x2 (* t x-scale) :y2 12
                         :stroke "#111" :stroke-width 1}))
             (for [t major-ticks]
               ($ :g {:key (str "hdr-tick-" t)}
                  ($ :line {:x1 (* t x-scale) :y1 8
                            :x2 (* t x-scale) :y2 12
                            :stroke "#111" :stroke-width 1})
                  ($ :text {:x (* t x-scale) :y 6
                            :text-anchor "middle"
                            :style {:font-family "monospace"
                                    :font-size "10px"
                                    :fill "#111"}}
                     (scale/format-label scale-origin max-depth t)))))
          ;; Spacer pushes headers to align with metadata columns
          ($ :div {:style {:width (str (- start-offset (:svg-padding-x LAYOUT)) "px")
                           :flex-shrink 0
                           :position "relative"}}
             ($ :div {:title "Drag to adjust tree-metadata gap"
                      :style {:position "absolute"
                              :right "0px"
                              :top "6px"
                              :height "20px"
                              :width (str gap-handle-width "px")
                              :cursor "col-resize"
                              :background "rgba(0,0,0,0.15)"
                              :z-index 3
                              :pointer-events "auto"
                              :user-select "none"}
                      :on-mouse-down (fn [e]
                                       (.preventDefault e)
                                       (reset! gap-dragging-ref true)
                                       (reset! gap-start-x-ref (.-clientX e))
                                       (reset! gap-start-ref (or tree-metadata-gap-px 0)))})
          )
          (for [{:keys [key label width spacing]} columns]
            (let [col-gap (+ (or col-spacing 0) (or spacing 0))]
              ($ :div {:key key
                       :style {:width (str (+ width col-gap) "px")
                               :flex-shrink 0
                               :position "relative"
                               :padding-right (str shift-handle-width "px")
                               :box-sizing "border-box"}}
                 ($ :span label)
                 ($ :div {:title "Drag to adjust column spacing"
                          :style {:position "absolute"
                                  :right (str col-handle-inset "px")
                                  :top "6px"
                                  :height "20px"
                                  :width (str shift-handle-width "px")
                                  :cursor "col-resize"
                                  :background "rgba(0,0,0,0.1)"}
                          :on-mouse-down (fn [e]
                                           (.preventDefault e)
                                           (reset! col-dragging-ref key)
                                           (reset! col-start-x-ref (.-clientX e))
                                           (reset! col-start-spacing-ref (or spacing 0)))}))))))))
(defui-with-spec StickyHeader
  [{:spec :app.specs/sticky-header-props :props props}]
  ($ StickyHeader* props))
#_(def StickyHeader StickyHeader*)


(s/def :app.specs/metadata-column-props
  (s/keys :req-un [:app.specs/tips
                   :app.specs/x-offset
                   :app.specs/y-scale
                   :app.specs/column-key
                   :app.specs/column-label
                   :app.specs/cell-height
                   :app.specs/col-width]))

(defui MetadataColumn*
  "Renders one column of metadata values as SVG text elements,
  with an in-SVG header label and subtle cell borders.

  Each value is vertically aligned with its corresponding tree tip.

  Props (see `::app.specs/metadata-column-props`):
  - `:tips`         - positioned leaf nodes with merged `:metadata`
  - `:x-offset`     - horizontal pixel position for this column
  - `:y-scale`      - vertical spacing multiplier
  - `:column-key`   - keyword identifying which metadata field to display
  - `:column-label` - display label for the column header
  - `:cell-height`  - height of each cell (typically = y-scale)
  - `:col-width`    - total width for this column (including spacing)"
  [{:keys [tips x-offset y-scale column-key column-label cell-height col-width]}]
  (let [header-y   (- (:svg-padding-y LAYOUT) 24)
        rect-x     x-offset
        rect-w     col-width]
    ($ :g
       ;; In-SVG column header
       ($ :text {:x x-offset
                 :y header-y
                 :dominant-baseline "central"
                 :style {:font-family "sans-serif"
                         :font-size "12px"
                         :font-weight "bold"}}
          column-label)

       ;; Data cells with borders
       (for [tip tips]
         (let [cy (+ (* (:y tip) y-scale) (:svg-padding-y LAYOUT))]
           ($ :g {:key (str column-key "-" (:name tip))}
              ;; Horizontal cell border (bottom edge only)
              ($ :line {:x1 rect-x :y1 (+ cy (/ cell-height 2))
                        :x2 (+ rect-x rect-w) :y2 (+ cy (/ cell-height 2))
                        :stroke "#e0e0e0" :stroke-width 0.5})
              ;; Cell text
              ($ :text {:x x-offset
                        :y cy
                        :dominant-baseline "central"
                        :style {:font-family "monospace"
                                :font-size "12px"}}
                 (get-in tip [:metadata column-key] "N/A"))))))))


(defui-with-spec MetadataColumn
  [{:spec :app.specs/metadata-column-props :props props}]
  ($ MetadataColumn* props))
#_(def MetadataColumn MetadataColumn*r*)


(s/def :app.specs/metadata-table-props
  (s/keys :req-un [:app.specs/active-cols
                   :app.specs/tips
                   :app.specs/start-offset
                   :app.specs/y-scale
                   :app.specs/col-spacing]))

(defui MetadataTable*
  "Renders all metadata columns as a group, computing per-column offsets.

  Props (see `::app.specs/metadata-table-props`):
  - `:active-cols`      - vector of column config maps
  - `:tips`             - positioned leaf nodes with merged metadata
  - `:start-offset`     - pixel x where metadata columns begin
  - `:y-scale`          - vertical tip spacing
  - `:col-spacing`      - extra horizontal gap between columns"
  [{:keys [active-cols tips start-offset y-scale col-spacing]}]
  (let [col-gaps (mapv (fn [col] (+ (or col-spacing 0) (or (:spacing col) 0)))
                       active-cols)
        offsets (reductions (fn [acc [col gap]] (+ acc (:width col) gap))
                            start-offset
                            (map vector active-cols col-gaps))]
    (let [last-idx    (dec (count active-cols))
          last-gap    (nth col-gaps last-idx 0)
          table-x1   (nth offsets 0)
          table-x2   (+ (nth offsets last-idx)
                        (:width (nth active-cols last-idx))
                        last-gap)
          border-y   (- (:svg-padding-y LAYOUT) 10)]
      ($ :g
         ;; Solid header underline (fixed position, unaffected by vertical scaling)
         ($ :line {:x1 table-x1 :y1 border-y
                   :x2 table-x2 :y2 border-y
                   :stroke "#000" :stroke-width 0.5})

         (map-indexed
          (fn [idx col]
            (let [col-gap (nth col-gaps idx 0)]
              ($ MetadataColumn {:key (str "col-" (:key col))
                                 :tips tips
                                 :x-offset (nth offsets idx)
                                 :y-scale y-scale
                                 :column-key (:key col)
                                 :column-label (:label col)
                                 :cell-height y-scale
                                 :col-width (+ (:width col) col-gap)})))
          active-cols)))))

(defui-with-spec MetadataTable
  [{:spec :app.specs/metadata-table-props :props props}]
  ($ MetadataTable* props))