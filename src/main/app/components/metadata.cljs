(ns app.components.metadata
  "SVG rendering components for metadata column overlays.

  Contains [[StickyHeader]], [[MetadataColumn]], and [[MetadataTable]] —
  the components that render CSV/TSV metadata alongside the phylogenetic
  tree. All data arrives via props; these components do not access
  React context."
  (:require [cljs.spec.alpha :as s]
            [uix.core :refer [defui $]]
            [app.layout :refer [LAYOUT]]
            [app.components.scale :as scale]
            [app.specs :as specs])
  (:require-macros [app.specs :refer [defui-with-spec]]))

(defui StickyHeader
  "Renders a sticky header row displaying metadata column labels.

  Props (see `::app.specs/sticky-header-props`):
  - `:columns`      - seq of column config maps with `:key`, `:label`, `:width`
  - `:start-offset` - pixel offset where metadata columns begin
  - `:col-spacing`  - extra horizontal gap between columns (default 0)
  - `:max-depth`    - maximum x-coordinate in the tree
  - `:x-scale`      - horizontal scaling factor (pixels per branch-length unit)
  - `:scale-origin` - `:tips` or `:root` for scale labeling"
  [{:keys [columns start-offset col-spacing max-depth x-scale scale-origin]}]
  (let [scale-width (max 0 (- start-offset (:svg-padding-x LAYOUT)))
        {:keys [major-ticks minor-ticks]} (scale/scale-ticks {:max-depth max-depth
                                                              :x-scale x-scale
                                                              :origin scale-origin})]
    ($ :div {:style {:position "sticky"
                     :top 0
                     :z-index 10
                     :background "#f8f9fa"
                     :border-bottom "2px solid #dee2e6"
                     :height (str (:header-height LAYOUT) "px")
                     :display "flex"
                     :align-items "center"
                     :padding-left (str (:svg-padding-x LAYOUT) "px")
                     :font-family "sans-serif"
                     :font-size "12px"
                     :font-weight "bold"}}
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
                        :flex-shrink 0}})
       (for [{:keys [key label width]} columns]
         ($ :div {:key key :style {:width (str (+ width (or col-spacing 0)) "px") :flex-shrink 0}}
            label)))))

(defui MetadataColumn
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
  (let [offsets (reductions (fn [acc col] (+ acc (:width col) col-spacing))
                            start-offset
                            active-cols)]
    (let [last-idx    (dec (count active-cols))
          table-x1   (nth offsets 0)
          table-x2   (+ (nth offsets last-idx)
                        (:width (nth active-cols last-idx))
                        col-spacing)
          border-y   (- (:svg-padding-y LAYOUT) 10)]
      ($ :g
         ;; Solid header underline (fixed position, unaffected by vertical scaling)
         ($ :line {:x1 table-x1 :y1 border-y
                   :x2 table-x2 :y2 border-y
                   :stroke "#000" :stroke-width 0.5})

         (map-indexed
          (fn [idx col]
            ($ MetadataColumn {:key (str "col-" (:key col))
                               :tips tips
                               :x-offset (nth offsets idx)
                               :y-scale y-scale
                               :column-key (:key col)
                               :column-label (:label col)
                               :cell-height y-scale
                               :col-width (+ (:width col) col-spacing)}))
          active-cols)))))

(defui-with-spec MetadataTable
  [{:spec :app.specs/metadata-table-props :props props}]
  ($ MetadataTable* props))