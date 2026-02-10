(ns app.components.tree
  "SVG rendering components for phylogenetic tree nodes and branches.

  Contains [[Branch]], [[TreeNode]], and [[PhylogeneticTree]] — the
  recursive SVG components that draw the tree topology. All data
  arrives via props; these components do not access React context.

  Highlighting uses a two-tier model:
  - `highlights` — persistent map of `{leaf-name -> color-string}` for
    color-assigned nodes (filled circle in the assigned color)
  - `selected-ids` — transient set of leaf names currently checked in
    the AG-Grid (shown with a selection ring stroke)

  Clicking a leaf node toggles its membership in `selected-ids`,
  which is reflected in both the tree and the AG-Grid table."
  (:require [uix.core :refer [defui $]]
            [app.layout :refer [LAYOUT]]
            [app.components.scale :as scale]))

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

  Clicking a leaf's marker or label toggles its selection state
  via the `:on-toggle-selection` callback.

  Rendering priority for leaf markers:
  1. **Highlighted** (in `highlights` map) — filled circle in the
     node's assigned color, enlarged radius
  2. **Selected** (in `selected-ids` set) — shown with a dashed
     selection ring stroke
  3. **Default** — standard marker fill and radius

  Props (see `::app.specs/tree-node-props`):
  - `:node`                   - positioned tree node map
  - `:parent-x`               - parent's x-coordinate (unscaled)
  - `:parent-y`               - parent's y-coordinate (unscaled)
  - `:x-scale`                - horizontal scaling factor
  - `:y-scale`                - vertical spacing in pixels between adjacent tips
  - `:show-internal-markers`  - boolean, whether to render circles on internal nodes
  - `:show-distance-from-origin`   - boolean, whether to render internal node distances
  - `:scale-origin`         - `:tips` or `:root` for distance labeling
  - `:max-depth`            - maximum x-coordinate in the tree
  - `:marker-radius`          - radius of the circular node marker in pixels
  - `:marker-fill`            - default fill color for node markers
  - `:highlights`             - map of {leaf-name -> color-string} for highlighted nodes
  - `:selected-ids`           - set of leaf names currently selected in the grid
  - `:on-toggle-selection`    - `(fn [leaf-name])` callback to toggle selection"
  [{:keys [node parent-x parent-y x-scale y-scale show-internal-markers show-distance-from-origin
           scale-origin max-depth marker-radius marker-fill highlights selected-ids on-toggle-selection]}]
  (let [scaled-x (* (:x node) x-scale)
        scaled-y (* (:y node) y-scale)
        p-x (* parent-x x-scale)
        p-y (* parent-y y-scale)
        line-width 0.5
        line-color "#000"
        is-leaf? (empty? (:children node))
        node-name (:name node)
        highlight-color (when (and is-leaf? highlights) (get highlights node-name))
        selected? (and is-leaf? selected-ids (contains? selected-ids node-name))
        fill (if highlight-color highlight-color marker-fill)
        radius (if highlight-color (+ marker-radius 1.5) marker-radius)
        node-depth (:x node)
        label-value (when (and (not is-leaf?) show-distance-from-origin (number? node-depth) (pos? max-depth))
                      (scale/label-value scale-origin max-depth node-depth))
        distance-label (when (some? label-value)
                         (.toFixed (js/Number label-value) 1))
        on-click (when (and is-leaf? on-toggle-selection)
                   (fn [_e] (on-toggle-selection node-name)))]
    ($ :g
       ($ Branch {:x scaled-x :y scaled-y :parent-x p-x :parent-y p-y :line-color line-color :line-width line-width})

       ;; Node marker — always on leaves, optionally on internal nodes
       (when (or is-leaf? show-internal-markers)
         ($ :circle {:cx scaled-x :cy scaled-y :r radius :fill fill
                     :style (when on-click {:cursor "pointer"})
                     :on-click on-click}))

       ;; Selection ring for selected leaves
       (when selected?
         ($ :circle {:cx scaled-x :cy scaled-y :r (+ marker-radius 3)
                     :fill "none" :stroke "#666" :stroke-width 1.5
                     :stroke-dasharray "3 2"
                     :style {:pointer-events "none"}}))

       ;; Internal node distance label
       (when distance-label
         ($ :text {:x (- scaled-x 6)
                   :y (- scaled-y 6)
                   :text-anchor "end"
                   :style {:font-family "monospace"
                           :font-size "10px"
                           :fill "#111"}}
            distance-label))
       ;; Tip label
       (when is-leaf?
         ($ :text {:x (+ scaled-x 8)
                   :y scaled-y
                   :dominant-baseline "central"
                   :style {:font-family "monospace" :font-size "12px" :font-weight "bold"
                           :cursor (when on-click "pointer")}
                   :on-click on-click}
            node-name))

       ;; Recurse into children
       (for [child (:children node)]
         ($ TreeNode {:key (:id child)
                      :node child
                      :parent-x (:x node)
                      :parent-y (:y node)
                      :x-scale x-scale
                      :y-scale y-scale
                      :show-internal-markers show-internal-markers
                      :show-distance-from-origin show-distance-from-origin
                      :scale-origin scale-origin
                      :max-depth max-depth
                      :marker-radius marker-radius
                      :marker-fill marker-fill
                      :highlights highlights
                      :selected-ids selected-ids
                      :on-toggle-selection on-toggle-selection})))))

(defui PhylogeneticTree
  "Renders the phylogenetic tree as a positioned SVG group.

  A thin wrapper that places a `<g>` with the standard SVG padding
  transform and delegates recursive node rendering to [[TreeNode]].

  Props (see `::app.specs/phylogenetic-tree-props`):
  - `:tree`                   - positioned root node (recursive map)
  - `:x-scale`                - horizontal scaling factor
  - `:y-scale`                - vertical tip spacing
  - `:show-internal-markers`  - whether to render circles on internal nodes
  - `:show-distance-from-origin`    - whether to render internal node distances
  - `:marker-radius`          - radius of the circular node marker in pixels
  - `:marker-fill`            - fill color for node markers
  - `:highlights`             - map of {leaf-name -> color-string} for highlighted nodes
  - `:selected-ids`           - set of leaf names currently selected in the grid
  - `:on-toggle-selection`    - `(fn [leaf-name])` callback to toggle selection"
  [{:keys [tree x-scale y-scale show-internal-markers show-distance-from-origin scale-origin max-depth marker-radius marker-fill
           highlights selected-ids on-toggle-selection]}]
  ($ :g {:transform (str "translate(" (:svg-padding-x LAYOUT) ", " (:svg-padding-y LAYOUT) ")")}
     ($ TreeNode {:node tree
                  :parent-x 0
                  :parent-y (:y tree)
                  :x-scale x-scale
                  :y-scale y-scale
                  :show-internal-markers show-internal-markers
                  :show-distance-from-origin show-distance-from-origin
                  :scale-origin scale-origin
                  :max-depth max-depth
                  :marker-radius marker-radius
                  :marker-fill marker-fill
                  :highlights highlights
                  :selected-ids selected-ids
                  :on-toggle-selection on-toggle-selection})))
