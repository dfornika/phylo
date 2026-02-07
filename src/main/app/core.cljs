(ns app.core
  "Main application namespace for Phylo, a phylogenetic tree viewer.

  Provides tree layout algorithms, SVG rendering components (via UIx/React),
  metadata column overlays, and the application entry point. The key
  data flow is:

    Newick string -> parsed tree -> positioned tree -> SVG rendering

  Shared state (Newick string, metadata, zoom settings) is managed by
  [[app.state]] and accessed via React context. See [[app.specs]] for
  the shape of data structures used throughout."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.newick :as newick]
            [app.csv :as csv]
            [app.state :as state]))

;; ===== Layout Constants =====

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
   :metadata-gap 40
   :default-col-width 120
   :toolbar-gap 20
   :node-marker-radius 3
   :node-marker-fill "#333"})

;; ===== Tree Layout Functions =====

(defn get-max-x
  "Returns the maximum x-coordinate across all nodes in a positioned tree.

  Recursively traverses the tree comparing `:x` values. Used to determine
  the horizontal extent of the tree for scaling calculations."
  [node]
  (if (empty? (:children node))
    (:x node)
    (apply max (:x node) (map get-max-x (:children node)))))

(defn count-tips
  "Counts the number of leaf nodes (tips) in a tree.

  A tip is any node with an empty `:children` vector. Internal nodes
  contribute the sum of their children's tip counts."
  [node]
  (if (empty? (:children node))
    1
    (reduce + (map count-tips (:children node)))))

(defn assign-y-coords
  "Assigns vertical (y) coordinates to every node in the tree.

  Leaf nodes receive sequential integer y-values starting from the
  current value of the `next-y` atom. Internal nodes are positioned
  at the average y of their first and last child, producing a
  standard phylogenetic tree layout.

  Returns a tuple of `[updated-node next-y-value]`.

  `next-y` is a mutable atom that tracks the next available y position.
  It is shared across the entire traversal to ensure leaves are
  evenly spaced."
  [node next-y]
  (if (empty? (:children node))
    [(assoc node :y @next-y) (swap! next-y inc)]
    (let [processed-children (mapv #(first (assign-y-coords % next-y)) (:children node))
          avg-y (/ (+ (:y (first processed-children))
                      (:y (last processed-children))) 2)]
      [(assoc node :children processed-children :y avg-y) @next-y])))

(defn assign-x-coords
  "Assigns horizontal (x) coordinates by accumulating branch lengths.

  Each node's x-position is its parent's x plus its own `:branch-length`.
  The root node's branch length is ignored (pinned at x=0) so that
  the tree starts at the left edge.

  Single-arity call is the public entry point; multi-arity is used
  internally for recursion."
  ([node]
   (assign-x-coords node 0 true))
  ([node current-x is-root?]
   (let [len (if is-root? 0 (or (:branch-length node) 0))
         new-x (+ current-x len)]
     (assoc node :x new-x
            :children (mapv #(assign-x-coords % new-x false)
                            (:children node))))))

;; ===== File I/O =====

(defn read-file!
  "Reads a text file selected by the user via an `<input type=\"file\">` element.

  Extracts the first file from the JS event's target, reads it as text
  using a `FileReader`, and calls `on-read-fn` with the string content
  when loading completes. This is a side-effecting function (note the `!`)."
  [js-event on-read-fn]
  (when-let [file (-> js-event .-target .-files (aget 0))]
    (let [reader (js/FileReader.)]
      (set! (.-onload reader)
            (fn [e] (on-read-fn (-> e .-target .-result))))
      (.readAsText reader file))))

;; ===== Scale Bar Helpers =====

(defn calculate-scale-unit
  "Calculates a human-friendly tick interval for a scale bar.

  Given a maximum value, returns a 'nice' unit size based on the
  order of magnitude. The algorithm picks the largest round number
  that produces a reasonable number of ticks:
  - ratio < 2 -> 10% of magnitude
  - ratio < 5 -> 50% of magnitude
  - otherwise  -> full magnitude

  For example, `(calculate-scale-unit 0.37)` returns `0.05`."
  [max-x]
  (let [log10 (js/Math.log10 max-x)
        magnitude (js/Math.pow 10 (js/Math.floor log10))
        ratio (/ max-x magnitude)]
    (cond
      (< ratio 2) (* magnitude 0.1)
      (< ratio 5) (* magnitude 0.5)
      :else magnitude)))

(defn get-ticks
  "Generates a lazy sequence of tick positions from 0 to `max-x` in
  increments of `unit`. Used to render scale bar gridlines and labels.

  Guards against non-positive `unit` to avoid a non-terminating sequence:
  - If `max-x` is <= 0, returns a single tick at 0.
  - If `unit` is <= 0 (and `max-x` is > 0), returns an empty sequence."
  [max-x unit]
  (cond
    (<= max-x 0) [0]
    (<= unit 0)  []
    :else        (take-while #(<= % max-x)
                              (iterate #(+ % unit) 0))))

;; ===== Tree Traversal Helpers =====

(defn get-leaves
  "Collects all leaf nodes from a tree into a flat vector.

  Traverses the tree depth-first and returns only nodes whose
  `:children` vector is empty. Preserves left-to-right order."
  [n]
  (if (empty? (:children n))
    [n]
    (into [] (mapcat get-leaves (:children n)))))


;; ===== Tree Preparation =====

(defn assign-node-ids
  "Assigns a unique `:id` to every node in the tree for stable React keys.
  
  Traverses the tree depth-first, assigning sequential integer IDs starting
  from 0. The ID counter is passed as an atom to maintain state across
  recursive calls.
  
  Single-arity version returns the updated node with `:id` on every node.
  Two-arity version (internal) returns a tuple of `[updated-node next-id-value]`."
  ([node]
   (first (assign-node-ids node (atom 0))))
  ([node next-id]
   (let [current-id @next-id
         _ (swap! next-id inc)
         updated-children (mapv #(first (assign-node-ids % next-id))
                                (:children node))]
     [(assoc node :id current-id :children updated-children) @next-id])))

(defn prepare-tree
  "Builds a fully positioned tree with enriched leaf metadata.

  Pipeline: Newick string -> parsed tree -> y-positioned -> x-positioned,
  then collects leaves and merges metadata from uploaded CSV/TSV rows.

  Returns a map with:
  - `:tree`      - root node with `:x` and `:y` on every node
  - `:tips`      - flat vector of leaf nodes with `:metadata` merged
  - `:max-depth` - maximum x-coordinate (for scale calculations)"
  [newick-str metadata-rows active-cols]
  (let [root (-> (newick/newick->map newick-str)
                 (assign-y-coords (atom 0))
                 first
                 assign-x-coords
                 assign-node-ids)
        leaves (get-leaves root)
        id-key (-> active-cols first :key)
        metadata-index (into {} (map (fn [r] [(get r id-key) r]) metadata-rows))
        enriched-leaves (mapv #(assoc % :metadata (get metadata-index (:name %)))
                              leaves)]
    {:tree root
     :tips enriched-leaves
     :max-depth (get-max-x root)}))

;; ===== Date Filtering =====

(defn compute-highlight-set
  "Computes the set of leaf names whose metadata date values fall within
  the given date range.

  `metadata-rows` is the vector of row maps. `id-key` is the keyword
  for the ID column (first column). `date-col` is the keyword for the
  date column to filter on. `date-range` is `[start end]` of normalized
  YYYY-MM-DD strings.

  Returns a set of ID strings (leaf names) that are within range, or
  nil if inputs are incomplete."
  [metadata-rows id-key date-col date-range]
  (when (and date-col date-range id-key)
    (let [[start end] date-range]
      (when (and (not (str/blank? start)) (not (str/blank? end)))
        (into #{}
              (keep (fn [row]
                      (when-let [normalized (csv/parse-date (get row date-col))]
                        (when (and (>= (compare normalized start) 0)
                                   (<= (compare normalized end) 0))
                          (get row id-key)))))
              metadata-rows)))))

;; ===== UI Components =====

(defui MetadataHeader
  "Renders a sticky header row displaying metadata column labels.

  Props (see `::app.specs/metadata-header-props`):
  - `:columns`      - seq of column config maps with `:key`, `:label`, `:width`
  - `:start-offset` - pixel offset where metadata columns begin"
  [{:keys [columns start-offset]}]
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
     ;; Spacer pushes headers to align with metadata columns
     ($ :div {:style {:width (str (- start-offset (:svg-padding-x LAYOUT)) "px") :flex-shrink 0}}
        "Phylogeny")

     (for [{:keys [key label width]} columns]
       ($ :div {:key key :style {:width (str width "px") :flex-shrink 0}}
          label))))

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

(defui Branch
  "Renders a single tree branch as two SVG lines: a horizontal segment
  (the branch itself) and a vertical connector to the parent node.

  Props (see `::app.specs/branch-props`):
  - `:x`, `:y`             - endpoint (child) coordinates
  - `:parent-x`, `:parent-y` - start (parent) coordinates
  - `:line-color`          - stroke color string
  - `:line-width`          - stroke width in pixels"
  [{:keys [x y parent-x parent-y line-color line-width]}]
  ($ :g
     ;; Horizontal branch
     ($ :line {:x1 parent-x :y1 y :x2 x :y2 y :stroke line-color :stroke-width line-width})
     ;; Vertical connector to siblings
     ($ :line {:x1 parent-x :y1 parent-y :x2 parent-x :y2 y :stroke line-color :stroke-width line-width})))

(defui TreeNode
  "Recursively renders a tree node and all its descendants as SVG.

  Draws the branch connecting this node to its parent, renders a
  text label and circle marker for leaf nodes, optionally renders
  circle markers on internal nodes, and recurses into children.

  When `highlight-set` is provided and the leaf's name is in the set,
  the marker circle uses `highlight-color` instead of `marker-fill`.

  Props (see `::app.specs/tree-node-props`):
  - `:node`                   - positioned tree node map
  - `:parent-x`               - parent's x-coordinate (unscaled)
  - `:parent-y`               - parent's y-coordinate (unscaled)
  - `:x-scale`                - horizontal scaling factor
  - `:y-scale`                - vertical spacing in pixels between adjacent tips
  - `:show-internal-markers`  - boolean, whether to render circles on internal nodes
  - `:marker-radius`          - radius of the circular node marker in pixels
  - `:marker-fill`            - default fill color for node markers
  - `:highlight-set`          - (optional) set of leaf names to highlight
  - `:highlight-color`        - (optional) CSS color for highlighted markers"
  [{:keys [node parent-x parent-y x-scale y-scale show-internal-markers
           marker-radius marker-fill highlight-set highlight-color]}]
  (let [scaled-x (* (:x node) x-scale)
        scaled-y (* (:y node) y-scale)
        p-x (* parent-x x-scale)
        p-y (* parent-y y-scale)
        line-width 0.5
        line-color "#000"
        is-leaf? (empty? (:children node))
        highlighted? (and is-leaf? highlight-set (contains? highlight-set (:name node)))
        fill (if highlighted? (or highlight-color "#4682B4") marker-fill)]
    ($ :g
       ($ Branch {:x scaled-x :y scaled-y :parent-x p-x :parent-y p-y :line-color line-color :line-width line-width})

       ;; Node marker — always on leaves, optionally on internal nodes
       (when (or is-leaf? show-internal-markers)
         ($ :circle {:cx scaled-x :cy scaled-y :r (if highlighted? (+ marker-radius 1.5) marker-radius) :fill fill}))

       ;; Tip label
       (when is-leaf?
         ($ :text {:x (+ scaled-x 8)
                   :y scaled-y
                   :dominant-baseline "central"
                   :style {:font-family "monospace" :font-size "12px" :font-weight "bold"}}
            (:name node)))

       ;; Recurse into children
       (for [child (:children node)]
         ($ TreeNode {:key (:id child)
                      :node child
                      :parent-x (:x node)
                      :parent-y (:y node)
                      :x-scale x-scale
                      :y-scale y-scale
                      :show-internal-markers show-internal-markers
                      :marker-radius marker-radius
                      :marker-fill marker-fill
                      :highlight-set highlight-set
                      :highlight-color highlight-color})))))

(defn compute-min-max-dates
  "Computes minimum and maximum dates from a collection of date strings.
  
  Takes a collection of strings and returns a map with `:min-date` and 
  `:max-date` keys, or nil if no valid dates are found. Uses a single-pass
  reduce for O(n) performance instead of sorting.
  
  Args:
  - `date-strs` - collection of date strings to parse
  
  Returns:
  - `{:min-date \"YYYY-MM-DD\" :max-date \"YYYY-MM-DD\"}` or `nil`"
  [date-strs]
  (let [dates (keep csv/parse-date date-strs)]
    (when (seq dates)
      (reduce (fn [acc date]
                (-> acc
                    (update :min-date #(if % (if (< date %) date %) date))
                    (update :max-date #(if % (if (> date %) date %) date))))
              {:min-date nil :max-date nil}
              dates))))

(defui DateRangeFilter
  "Renders a date range filter control group in the toolbar.

  Reads date filter state and metadata columns from context.
  Shows a dropdown of detected date columns, two date inputs,
  a color picker, and a clear button.

  Requires no props — reads all state via [[app.state/use-app-state]]."
  [_props]
  (let [{:keys [active-cols metadata-rows
                date-filter-col set-date-filter-col!
                date-filter-range set-date-filter-range!
                highlight-color set-highlight-color!]} (state/use-app-state)
        date-cols (filterv #(= :date (:type %)) active-cols)
        ;; Compute min/max dates from the selected column in O(n) time
        col-dates (uix/use-memo
                   (fn []
                     (when date-filter-col
                       (compute-min-max-dates
                        (mapv #(get % date-filter-col) metadata-rows))))
                   [date-filter-col metadata-rows])
        start-date (first date-filter-range)
        end-date (second date-filter-range)]
    ($ :div {:style {:display "flex" :gap "8px" :padding "10px"
                     :background "#e8f0fe" :border-radius "4px"
                     :align-items "center" :flex-wrap "wrap"}}
       ($ :label {:style {:font-weight "bold" :font-size "12px"}} "Date Filter:")
       ;; Column selector
       ($ :select {:value (or (some-> date-filter-col name) "")
                   :on-change (fn [e]
                                (let [v (.. e -target -value)]
                                  (if (str/blank? v)
                                    (do (set-date-filter-col! nil)
                                        (set-date-filter-range! nil))
                                    (let [col-kw (keyword v)
                                          min-max (compute-min-max-dates
                                                   (mapv #(get % col-kw) metadata-rows))]
                                      (set-date-filter-col! col-kw)
                                      (when min-max
                                        (set-date-filter-range! [(:min-date min-max) (:max-date min-max)]))))))}
          ($ :option {:value ""} "Select column...")
          (for [col date-cols]
            ($ :option {:key (:key col) :value (name (:key col))} (:label col))))
       ;; Date inputs
       (when date-filter-col
         ($ :<>
            ($ :label {:style {:font-size "11px"}} "From:")
            ($ :input {:type "date"
                       :value (or start-date "")
                       :min (:min-date col-dates)
                       :max (:max-date col-dates)
                       :on-change (fn [e]
                                    (let [v (.. e -target -value)]
                                      (set-date-filter-range! [v (or end-date v)])))})
            ($ :label {:style {:font-size "11px"}} "To:")
            ($ :input {:type "date"
                       :value (or end-date "")
                       :min (:min-date col-dates)
                       :max (:max-date col-dates)
                       :on-change (fn [e]
                                    (let [v (.. e -target -value)]
                                      (set-date-filter-range! [(or start-date v) v])))})
            ;; Color picker
            ($ :label {:style {:font-size "11px"}} "Color:")
            ($ :input {:type "color"
                       :value highlight-color
                       :on-change (fn [e]
                                    (set-highlight-color! (.. e -target -value)))
                       :style {:width "30px" :height "24px" :border "none"
                               :padding "0" :cursor "pointer"}})
            ;; Clear button
            ($ :button {:on-click (fn [_]
                                    (set-date-filter-col! nil)
                                    (set-date-filter-range! nil))
                        :style {:font-size "11px" :padding "2px 8px"
                                :cursor "pointer"}}
               "Clear"))))))

(defui Toolbar
  "Renders the control panel with file loaders, layout sliders, and date filter.

  Reads all state from [[app.state/app-context]] via [[app.state/use-app-state]],
  so this component requires no props."
  [_props]
  (let [{:keys [x-mult set-x-mult!
                y-mult set-y-mult!
                col-spacing set-col-spacing!
                show-internal-markers set-show-internal-markers!
                show-scale-gridlines set-show-scale-gridlines!
                show-pixel-grid set-show-pixel-grid!
                set-newick-str!
                set-metadata-rows! set-active-cols!]} (state/use-app-state)]
    ($ :div {:style {:padding "12px"
                     :background "#f8f9fa"
                     :border-bottom "1px solid #ddd"
                     :display "flex"
                     :gap (str (:toolbar-gap LAYOUT) "px")
                     :align-items "center"
                     :flex-wrap "wrap"}}
       ($ :div {:style {:display "flex" :gap "10px" :padding "10px" :background "#eee" :border-radius "4px"}}
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Load Tree (Newick): ")
             ($ :input {:type "file"
                        :accept ".nwk,.newick,.tree,.txt"
                        :on-change #(read-file! % (fn [content]
                                                    (set-newick-str! (.trim content))))}))
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Load Metadata (CSV/TSV): ")
             ($ :input {:type "file"
                        :accept ".csv,.tsv,.txt"
                        :on-change #(read-file! % (fn [content]
                                                    (let [{:keys [headers data]} (csv/parse-metadata content (:default-col-width LAYOUT))]
                                                      (set-metadata-rows! data)
                                                      (set-active-cols! headers))))})))
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Tree Width: ")
          ($ :input {:type "range"
                     :min 0.05
                     :max 1.5
                     :step 0.01
                     :value x-mult
                     :on-change #(set-x-mult! (js/parseFloat (.. % -target -value)))}))
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Tree Height: ")
          ($ :input {:type "range"
                     :min 10
                     :max 100
                     :value y-mult
                     :on-change #(set-y-mult! (js/parseInt (.. % -target -value) 10))}))
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Column Spacing: ")
          ($ :input {:type "range"
                     :min 0
                     :max 50
                     :step 1
                     :value col-spacing
                     :on-change #(set-col-spacing! (js/parseInt (.. % -target -value) 10))}))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-internal-markers-checkbox"
                     :checked show-internal-markers
                     :on-change #(set-show-internal-markers! (not show-internal-markers))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-internal-markers-checkbox"}} "Show internal node markers"))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-scale-gridlines-checkbox"
                     :checked show-scale-gridlines
                     :on-change #(set-show-scale-gridlines! (not show-scale-gridlines))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-scale-gridlines-checkbox"}} "Show scale gridlines"))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-pixel-grid-checkbox"
                     :checked show-pixel-grid
                     :on-change #(set-show-pixel-grid! (not show-pixel-grid))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-pixel-grid-checkbox"}} "Show pixel grid"))
       ;; Date range filter
       ($ DateRangeFilter))))


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

  Computes human-friendly tick intervals via [[calculate-scale-unit]] and
  [[get-ticks]], then draws one dashed line per tick across the full
  `tree-height`. Intended to be placed as a sibling of the tree and
  metadata in the SVG, inside the translated coordinate group.

  Props (see `::app.specs/scale-gridlines-props`):
  - `:max-depth`   - maximum x-coordinate in the tree
  - `:x-scale`     - horizontal scaling factor (pixels per branch-length unit)
  - `:tree-height` - total height in pixels to span"
  [{:keys [max-depth x-scale tree-height]}]
  (if (pos? max-depth)
    (let [unit  (calculate-scale-unit (/ max-depth 5))
          ticks (get-ticks max-depth unit)]
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

(defui PhylogeneticTree
  "Renders the phylogenetic tree as a positioned SVG group.

  A thin wrapper that places a `<g>` with the standard SVG padding
  transform and delegates recursive node rendering to [[TreeNode]].

  Props (see `::app.specs/phylogenetic-tree-props`):
  - `:tree`                   - positioned root node (recursive map)
  - `:x-scale`                - horizontal scaling factor
  - `:y-scale`                - vertical tip spacing
  - `:show-internal-markers`  - whether to render circles on internal nodes
  - `:marker-radius`          - radius of the circular node marker in pixels
  - `:marker-fill`            - fill color for node markers
  - `:highlight-set`          - (optional) set of leaf names to highlight
  - `:highlight-color`        - (optional) CSS color for highlighted markers"
  [{:keys [tree x-scale y-scale show-internal-markers marker-radius marker-fill
           highlight-set highlight-color]}]
  ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
     ($ TreeNode {:node tree
                  :parent-x 0
                  :parent-y (:y tree)
                  :x-scale x-scale
                  :y-scale y-scale
                  :show-internal-markers show-internal-markers
                  :marker-radius marker-radius
                  :marker-fill marker-fill
                  :highlight-set highlight-set
                  :highlight-color highlight-color})))

(defui MetadataTable
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

(defn kebab-case->camelCase
  "Converts between kebab-case and camelCase"
  [k]
  (let [words (str/split (name k) #"-")]
    (->> (map str/capitalize (rest words))
         (apply str (first words))
         keyword)))

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

          ($ :svg {:width svg-width :height svg-height}
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
  the positioned tree via [[prepare-tree]] (memoized), computes the
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
                                       (fn [] (prepare-tree newick-str metadata-rows active-cols))
                                       [newick-str metadata-rows active-cols])

        id-key (-> active-cols first :key)

        highlight-set (uix/use-memo
                       (fn [] (compute-highlight-set metadata-rows id-key date-filter-col date-filter-range))
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

;; ===== App Shell =====

(defui app
  "Root application component.

  Wraps the component tree with [[state/AppStateProvider]] so all
  descendants can access shared state via context."
  []
  ($ state/AppStateProvider
     ($ TreeContainer {:width-px 1200
                       :component-height-px 800})))


(defonce root
  (when (exists? js/document)
    (when-let [el (js/document.getElementById "app")]
      (uix.dom/create-root el))))

(defn render
  "Renders the root [[app]] component into the DOM."
  []
  (when root
    (uix.dom/render-root ($ app) root)))

(defn ^:export init
  "Exported entry point called by shadow-cljs on page load."
  []
  (render))

(defn ^:dev/after-load re-render
  "Hot-reload hook called by shadow-cljs after code changes.
  Re-renders from root so that new component definitions take effect.
  State is preserved because it lives in `defonce` atoms in [[app.state]]."
  []
  (render))
