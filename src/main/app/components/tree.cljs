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
  (:require [cljs.spec.alpha :as s]
            [uix.core :refer [defui $]]
            [app.layout :refer [LAYOUT]]
            [app.scale :as scale]
            [app.specs :as specs])
  (:require-macros [app.specs :refer [defui-with-spec]]))

(s/def :app.specs/branch-props
  (s/keys :req-un [:app.specs/x
                   :app.specs/y
                   :app.specs/parent-x
                   :app.specs/parent-y
                   :app.specs/line-color
                   :app.specs/line-width]))

(defui Branch*
  "Renders a single tree branch as two SVG lines: a horizontal segment
  (the branch itself) and a vertical connector to the parent node.

  Props (see `:app.specs/branch-props`):
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

(defui-with-spec Branch
  [{:spec :app.specs/branch-props :props props}]
  ($ Branch* props))
#_(def Branch Branch*)

(s/def :app.specs/tree-node-props
  (s/keys :req-un [:app.specs/node
                   :app.specs/parent-x
                   :app.specs/parent-y
                   :app.specs/x-scale
                   :app.specs/y-scale
                   :app.specs/show-internal-markers
                   :app.specs/marker-radius
                   :app.specs/marker-fill
                   :app.specs/show-distance-from-origin
                   :app.specs/scale-origin
                   :app.specs/max-depth]
          :opt-un [:app.specs/highlights
                   :app.specs/selected-ids
                   :app.specs/on-toggle-selection
                   :app.specs/on-select-subtree
                   :app.specs/active-reference-node-id
                   :app.specs/on-set-reroot-node
                   :app.specs/branch-length-mult
                   :app.specs/node-distances]))

(declare TreeNode)

(defui TreeNode*
  "Recursively renders a tree node and all its descendants as SVG.

  Draws the branch connecting this node to its parent, renders a
  text label and circle marker for leaf nodes, optionally renders
  circle markers on internal nodes, and recurses into children.

  Clicking a leaf's marker or label toggles its selection state
  via the `:on-toggle-selection` callback.
  Ctrl+clicking any node (leaf or internal) selects it for rerooting
  via `:on-set-reroot-node`.

  Rendering priority for leaf markers:
  1. **Highlighted** (in `highlights` map) — filled circle in the
     node's assigned color, enlarged radius
  2. **Selected** (in `selected-ids` set) — shown with a dashed
     selection ring stroke
  3. **Default** — standard marker fill and radius

  Props (see `:app.specs/tree-node-props`):
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
  - `:on-toggle-selection`    - `(fn [leaf-name])` callback to toggle selection
  - `:on-select-subtree`      - `(fn [node])` callback to add a subtree's leaf names"
  [{:keys [node parent-x parent-y x-scale y-scale show-internal-markers show-distance-from-origin
           scale-origin max-depth marker-radius marker-fill highlights selected-ids on-toggle-selection
           on-select-subtree active-reference-node-id on-set-reroot-node branch-length-mult
           node-distances]}]
  (let [scaled-x (* (:x node) x-scale)
        scaled-y (* (:y node) y-scale)
        p-x (* parent-x x-scale)
        p-y (* parent-y y-scale)
        line-width 0.5
        line-color "#000"
        is-leaf? (not (seq (:children node)))
        internal-node? (not is-leaf?)
        node-name (:name node)
        highlight-color (when (and is-leaf? highlights) (get highlights node-name))
        selected? (and is-leaf? selected-ids (contains? selected-ids node-name))
        fill (if highlight-color highlight-color marker-fill)
        radius (if highlight-color (+ marker-radius 1.5) marker-radius)
        node-depth (:x node)
        bl-mult    (or branch-length-mult 1)
        distance-label (when (and (not is-leaf?) show-distance-from-origin (number? node-depth) (pos? max-depth))
                         (scale/format-label scale-origin (* max-depth bl-mult) (* node-depth bl-mult)))
        node-dist  (when (and is-leaf? node-distances) (get node-distances node-name))
        node-dist-label (when node-dist
                          (let [decimals (scale/label-decimals (* max-depth bl-mult))]
                            (.toFixed node-dist decimals)))
        leaf-names (when internal-node?
                     (:leaf-names node))
        any-selected? (and (seq selected-ids)
                           (seq leaf-names)
                           (some leaf-names selected-ids))
        active-reference? (and active-reference-node-id
                               (= (:id node) active-reference-node-id))
        internal-state-class (when (seq leaf-names)
                               (if any-selected?
                                 " internal-node-marker--deselect"
                                 " internal-node-marker--select"))
        leaf-click (when (and is-leaf? on-toggle-selection)
                     (fn [e]
                       (if (and (.-ctrlKey e) on-set-reroot-node)
                         (on-set-reroot-node (when-not active-reference? (:id node)))
                         (on-toggle-selection node-name))))
        internal-click (when internal-node?
                         (fn [e]
                           (if (and (.-ctrlKey e) on-set-reroot-node)
                             ;; Ctrl+click: toggle reference node selection
                             (on-set-reroot-node (when-not active-reference? (:id node)))
                             ;; Otherwise: try subtree selection
                             (when on-select-subtree
                               (on-select-subtree node)))))
        internal-class (str "internal-node-marker"
                            (when (not show-internal-markers)
                              " internal-node-marker--hidden")
                            internal-state-class)
        internal-fill (if show-internal-markers marker-fill "none")
        internal-stroke (if show-internal-markers "#111" "none")
        internal-visible-radius (max 1 (- marker-radius 1))]
    ($ :g
       ($ Branch {:x scaled-x :y scaled-y :parent-x p-x :parent-y p-y :line-color line-color :line-width line-width})

       ;; Leaf marker
       (when is-leaf?
         ($ :circle {:cx scaled-x :cy scaled-y :r radius :fill fill
                     :style (when leaf-click {:cursor "pointer"})
                     :on-click leaf-click}))

       ;; Internal node marker (hidden until hover when toggled off)
       (when internal-node?
         ($ :g
            ($ :circle {:cx scaled-x :cy scaled-y :r (+ marker-radius 12)
                        :fill "none"
                        :stroke "none"
                        :class (str internal-class " internal-node-hit")
                        :style (merge {:pointer-events "all"}
                                      (when internal-click {:cursor "pointer"}))
                        :on-click internal-click})
            ($ :circle {:cx scaled-x :cy scaled-y :r internal-visible-radius
                        :fill internal-fill
                        :stroke internal-stroke
                        :stroke-width (when show-internal-markers 1)
                        :class internal-class
                        :style (when internal-click {:cursor "pointer"})
                        :on-click internal-click})
            ($ :circle {:cx scaled-x :cy scaled-y :r (+ marker-radius 4)
                        :fill "none"
                        :stroke "none"
                        :style {:pointer-events "none"}
                        :class "internal-node-hover-ring"})))

       ;; Selection ring for selected leaves
       (when selected?
         ($ :circle {:cx scaled-x :cy scaled-y :r (+ marker-radius 3)
                     :fill "none" :stroke "#666" :stroke-width 1.5
                     :stroke-dasharray "3 2"
                     :style {:pointer-events "none"}}))

       (when active-reference?
         ($ :circle {:cx scaled-x :cy scaled-y :r (+ marker-radius 6)
                     :fill "none"
                     :stroke "#d9534f"  ;; Red to indicate "this will be new root"
                     :stroke-width 2}))

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
                           :cursor (when leaf-click "pointer")}
                   :on-click leaf-click}
            node-name))

       ;; Distance from reference node label (below leaf name)
       (when node-dist-label
         ($ :text {:x (+ scaled-x 8)
                   :y (+ scaled-y 14)
                   :dominant-baseline "central"
                   :style {:font-family "monospace" :font-size "10px" :fill "#666"
                           :pointer-events "none"}}
            node-dist-label))

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
                      :branch-length-mult branch-length-mult
                      :node-distances node-distances
                      :marker-radius marker-radius
                      :marker-fill marker-fill
                      :highlights highlights
                      :selected-ids selected-ids
                      :active-reference-node-id active-reference-node-id
                      :on-set-reroot-node on-set-reroot-node
                      :on-toggle-selection on-toggle-selection
                      :on-select-subtree on-select-subtree})))))

(defui-with-spec TreeNode
  [{:spec :app.specs/tree-node-props :props props}]
  ($ TreeNode* props))
#_(def TreeNode TreeNode*)

(s/def :app.specs/phylogenetic-tree-props
  (s/keys :req-un [:app.specs/tree
                   :app.specs/x-scale
                   :app.specs/y-scale
                   :app.specs/show-internal-markers
                   :app.specs/marker-radius
                   :app.specs/marker-fill
                   :app.specs/show-distance-from-origin
                   :app.specs/scale-origin
                   :app.specs/max-depth]
          :opt-un [:app.specs/highlights
                   :app.specs/selected-ids
                   :app.specs/on-toggle-selection
                   :app.specs/on-select-subtree
                   :app.specs/active-reference-node-id
                   :app.specs/on-set-reroot-node
                   :app.specs/branch-length-mult
                   :app.specs/node-distances]))

(defui PhylogeneticTree*
  "Renders the phylogenetic tree as a positioned SVG group.

  A thin wrapper that places a `<g>` with the standard SVG padding
  transform and delegates recursive node rendering to [[TreeNode]].

  Props (see `:app.specs/phylogenetic-tree-props`):
  - `:tree`                   - positioned root node (recursive map)
  - `:x-scale`                - horizontal scaling factor
  - `:y-scale`                - vertical tip spacing
  - `:show-internal-markers`  - whether to render circles on internal nodes
  - `:show-distance-from-origin`    - whether to render internal node distances
  - `:marker-radius`          - radius of the circular node marker in pixels
  - `:marker-fill`            - fill color for node markers
  - `:highlights`             - map of {leaf-name -> color-string} for highlighted nodes
  - `:selected-ids`           - set of leaf names currently selected in the grid
  - `:active-reference-node-id` - ID of the node selected for rerooting (or nil)
  - `set-active-reference-node-id!` - setter for reroot node selection
  - `:on-toggle-selection`    - `(fn [leaf-name])` callback to toggle selection
  - `:on-select-subtree`      - `(fn [node])` callback to add a subtree's leaf names"

  [{:keys [tree x-scale y-scale show-internal-markers show-distance-from-origin scale-origin max-depth marker-radius marker-fill
           highlights selected-ids  active-reference-node-id set-active-reference-node-id! on-toggle-selection on-select-subtree
           branch-length-mult node-distances]}]
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
                  :branch-length-mult branch-length-mult
                  :node-distances node-distances
                  :marker-radius marker-radius
                  :marker-fill marker-fill
                  :highlights highlights
                  :selected-ids selected-ids
                  :active-reference-node-id active-reference-node-id
                  :on-set-reroot-node set-active-reference-node-id!
                  :on-toggle-selection on-toggle-selection
                  :on-select-subtree on-select-subtree})))

(defui-with-spec PhylogeneticTree
  [{:spec :app.specs/phylogenetic-tree-props :props props}]
  ($ PhylogeneticTree* props))
#_(def PhylogeneticTree PhylogeneticTree*)
