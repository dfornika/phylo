(ns app.components.tree
  "SVG rendering components for phylogenetic tree nodes and branches.

  Contains [[Branch]], [[TreeNode]], and [[PhylogeneticTree]] — the
  recursive SVG components that draw the tree topology. All data
  arrives via props; these components do not access React context."
  (:require [uix.core :refer [defui $]]
            [app.layout :refer [LAYOUT]]))

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
