(ns app.core
  "Main application namespace for Phylo, a phylogenetic tree viewer.

  Provides tree layout algorithms, SVG rendering components (via UIx/React),
  metadata column overlays, and the application entry point. The key
  data flow is:

    Newick string -> parsed tree -> positioned tree -> SVG rendering

  See [[app.specs]] for the shape of the data structures used throughout."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.newick :as newick]
            [app.csv :as csv]))

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
  - `:toolbar-gap`       - spacing between toolbar controls"
  {:svg-padding-x 40
   :svg-padding-y 40
   :header-height 36
   :label-buffer 150
   :metadata-gap 40
   :default-col-width 120
   :toolbar-gap 20})

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
  (let [file (-> js-event .-target .-files (aget 0))
        reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e] (on-read-fn (-> e .-target .-result))))
    (.readAsText reader file)))

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
    (into [](mapcat get-leaves (:children n)))))

(comment
  (let [test-nwk-str "(((A:0.2, B:0.3):0.3,(C:0.5, D:0.3):0.2):0.3, E:0.7):1.0;"]
    (get-leaves (newick/newick->map test-nwk-str)))
  )
  

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
     ($ :div {:style {:width (str (- start-offset (* 2 (:svg-padding-x LAYOUT))) "px") :flex-shrink 0}}
        "Phylogeny")

     (for [{:keys [key label width]} columns]
       ($ :div {:key key :style {:width (str width "px") :flex-shrink 0}}
          label))))

(defui MetadataColumn
  "Renders one column of metadata values as SVG text elements.

  Each value is vertically aligned with its corresponding tree tip.

  Props (see `::app.specs/metadata-column-props`):
  - `:tips`       - positioned leaf nodes with merged `:metadata`
  - `:x-offset`   - horizontal pixel position for this column
  - `:y-scale`    - vertical spacing multiplier
  - `:column-key` - keyword identifying which metadata field to display"
  [{:keys [tips x-offset y-scale column-key]}]
  ($ :g
     (for [tip tips]
       ($ :text {:key (str column-key "-" (:name tip))
                 :x x-offset
                 :y (+ (* (:y tip) y-scale) (:svg-padding-y LAYOUT))
                 :dominant-baseline "central"
                 :style {:font-family "monospace"
                         :font-size "12px"}}

          (get-in tip [:metadata column-key] "N/A")))))

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
  text label for leaf nodes, and recurses into children.

  Props (see `::app.specs/tree-node-props`):
  - `:node`     - positioned tree node map
  - `:parent-x` - parent's x-coordinate (unscaled)
  - `:parent-y` - parent's y-coordinate (unscaled)
  - `:x-scale`  - horizontal scaling factor (pixels per branch-length unit)
  - `:y-scale`  - vertical spacing in pixels between adjacent tips"
  [{:keys [node parent-x parent-y x-scale y-scale]}]
  (let [scaled-x (* (:x node) x-scale)
        scaled-y (* (:y node) y-scale)
        p-x (* parent-x x-scale)
        p-y (* parent-y y-scale)
        line-width 0.5
        line-color "#000"]
    ($ :g
       ($ Branch {:x scaled-x :y scaled-y :parent-x p-x :parent-y p-y :line-color line-color :line-width line-width})

       ;; Tip label
       (when (empty? (:children node))
         ($ :g
            ($ :text {:x (+ scaled-x 8)
                      :y scaled-y
                      :dominant-baseline "central"
                      :style {:font-family "monospace" :font-size "12px" :font-weight "bold"}}
               (:name node))))

       ;; Recurse into children
       (for [child (:children node)]
         ($ TreeNode {:key (:name child)
                      :node child
                      :parent-x (:x node)
                      :parent-y (:y node)
                      :x-scale x-scale
                      :y-scale y-scale})))))

(defui Toolbar
  "Renders the control panel with tree width/spacing sliders and a file
  upload input for loading metadata CSV/TSV files.

  Props (see `::app.specs/toolbar-props`):
  - `:x-mult`            - current horizontal scale multiplier
  - `:y-mult`            - current vertical spacing value
  - `:on-x-mult-change`  - callback for horizontal slider changes
  - `:on-y-mult-change`  - callback for vertical slider changes
  - `:on-metadata-load`  - callback for metadata file input changes"
  [{:keys [x-mult y-mult on-x-mult-change on-y-mult-change on-metadata-load]}]
  ($ :div {:style {:padding "12px"
                   :background "#f8f9fa"
                   :border-bottom "1px solid #ddd"
                   :display "flex"
                   :gap (str (:toolbar-gap LAYOUT) "px")}}
     ($ :div
        ($ :label {:style {:font-weight "bold"}} "Tree Width: ")
        ($ :input {:type "range"
                   :min 0.05
                   :max 1.5
                   :step 0.01
                   :value x-mult
                   :on-change on-x-mult-change}))
     ($ :div
        ($ :label {:style {:font-weight "bold"}} "Vertical Spacing: ")
        ($ :input {:type "range"
                   :min 10
                   :max 100
                   :value y-mult
                   :on-change on-y-mult-change}))
     ($ :div {:style {:display "flex" :gap "10px" :padding "10px" :background "#eee"}}
        ($ :div
           ($ :label "Load Metadata (CSV/TSV): ")
           ($ :input {:type "file"
                      :accept ".csv,.tsv,.txt"
                      :on-change on-metadata-load})))))

(defui PhylogeneticTree
  "Main visualization component that combines tree rendering with
  metadata columns in a scrollable SVG viewport.

  Manages local state for metadata rows, zoom multipliers, and
  active columns. Parses the Newick string, assigns coordinates,
  merges metadata into leaf nodes, and renders the full layout.

  Props (see `::app.specs/phylogenetic-tree-props`):
  - `:newick-str`         - Newick-format tree string to render
  - `:width-px`           - total available width in pixels
  - `:component-height-px` - total available height in pixels"
  [{:keys [newick-str width-px component-height-px]}]
  (let [[x-mult set-x-mult!] (uix/use-state 0.5)
        [y-mult set-y-mult!] (uix/use-state 30)
        [metadata-rows set-rows!] (uix/use-state [])
        [active-cols set-cols!] (uix/use-state [])

        ;; Process tree and merge metadata
        {:keys [tree tips max-depth]} (uix/use-memo
                                       (fn []
                                         (let [root (-> (newick/newick->map newick-str)
                                                        (assign-y-coords (atom 0))
                                                        first
                                                        assign-x-coords)
                                               leaves (get-leaves root)
                                               id-key (-> active-cols first :key)
                                               metadata-index (into {} (map (fn [r] [(get r id-key) r]) metadata-rows))
                                               enriched-leaves (mapv #(assoc % :metadata (get metadata-index (:name %)))
                                                                     leaves)]
                                           {:tree root
                                            :tips enriched-leaves
                                            :max-depth (get-max-x root)}))
                                       [newick-str metadata-rows active-cols])

        ;; Dynamic layout math
        current-x-scale (if (> max-depth 0)
                          (* (/ (- width-px 400) max-depth) x-mult)
                          1)
        tree-end-x (+ (* max-depth current-x-scale) (:label-buffer LAYOUT))
        metadata-start-x (+ tree-end-x (:metadata-gap LAYOUT))]

    ($ :div {:style {:display "flex" :flex-direction "column" :height (str component-height-px "px")}}

       ;; Toolbar
       ($ Toolbar {:x-mult x-mult
                   :y-mult y-mult
                   :on-x-mult-change #(set-x-mult! (js/parseFloat (.. % -target -value)))
                   :on-y-mult-change #(set-y-mult! (js/parseInt (.. % -target -value)))
                   :on-metadata-load #(read-file! % (fn [content]
                                                      (let [{:keys [headers data]} (csv/parse-metadata content (:default-col-width LAYOUT))]
                                                        (set-rows! data)
                                                        (set-cols! headers))))})

       ;; Scrollable viewport
       ($ :div {:style {:flex "1" :overflow "auto" :position "relative" :border-bottom "2px solid #dee2e6"}}
          (when (seq active-cols)
            ($ MetadataHeader {:columns active-cols :start-offset metadata-start-x}))

          ($ :svg {:width (+ metadata-start-x (reduce + (map :width active-cols)) 100)
                   :height (+ (* (count tips) y-mult) 100)}
             ;; Scale gridlines
             (let [unit (calculate-scale-unit (/ max-depth 5))
                   ticks (get-ticks max-depth unit)
                   tree-height (* (count tips) y-mult)
                   svg-transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")]
               ($ :g {:transform svg-transform}
                  ($ :g
                     (for [t ticks]
                       ($ :line {:key (str "grid-" t)
                                 :x1 (* t current-x-scale) :y1 0
                                 :x2 (* t current-x-scale) :y2 tree-height
                                 :stroke "#eee"
                                 :stroke-dasharray "4 4"
                                 :stroke-width 1})))
                  ($ TreeNode {:node tree :parent-x 0 :parent-y (:y tree)
                               :x-scale current-x-scale :y-scale y-mult})))

                ;; Metadata columns
                (let [offsets (reductions (fn [acc col] (+ acc (:width col)))
                                          metadata-start-x
                                          active-cols)]
                  (map-indexed
                    (fn [idx col]
                      ($ MetadataColumn {:key (str "col-" (:key col))
                                         :tips tips
                                         :x-offset (- (nth offsets idx) (:svg-padding-x LAYOUT))
                                         :y-scale y-mult
                                         :column-key (:key col)}))
                    active-cols)))))))

;; ===== Sample Data =====
(def dog-cat-tree "(Dog,Cat)Mammal;")
(def dog-cat-tree-with-distances "(Dog:0.1,Cat:0.2)Mammal:0.5;")

(def abc-tree
  "Sample Newick tree string with 31 taxa (A through AW).
  Used as the default tree for development and demonstration."
  "(((A:1.575,
B:1.575
)C:5.99484,
((D:5.1375,
(E:4.21625,
(F:1.32,
(G:0.525,
H:0.525
)I:0.795
)J:2.89625
)K:0.92125
)L:1.5993,
((M:2.895,
(N:2.11,
O:2.11
)P:0.785
)Q:3.1725,
R:6.0675
)S:0.6693
)T:1.50234
)U:2.86223,
((V:1.58,
(W:1.055,
X:1.055
)Y:0.525
)Z:5.17966,
(AA:4.60414,
(AB:2.95656,
((AC:1.8425,
(AD:0.525,
AE:0.525
)AF:1.3175
)AG:0.99844,
((AH:1.1975,
(AI:1.055,
(AJ:0,
AK:0
)AL:1.055
)AM:0.1425
)AN:0.92281,
(AO:1.58,
AP:1.58
)AQ:0.54031
)AR:1.26094
)AS:1.11406
)AT:1.64758
)AU:2.15552
)AV:4.32559
)AW:10.4109")

;; ===== App Shell =====

(defui app
  "Root application component.

  Renders the [[PhylogeneticTree]] with the sample [[abc-tree]] data
  at a fixed 1200x800 pixel viewport."
  []
  ($ PhylogeneticTree {:newick-str abc-tree
                       :width-px 1200
                       :component-height-px 800}))

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
  "Hot-reload hook called by shadow-cljs after code changes."
  []
  (render))
