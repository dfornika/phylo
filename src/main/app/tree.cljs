(ns app.tree
  "Functions for phylogenetic tree layout and analysis.

  Provides the pipeline from parsed Newick tree to fully positioned
  tree with enriched leaf metadata:

    parsed tree -> y-positioned -> x-positioned -> enriched leaves

  Most functions in this namespace are pure and operate on immutable data.
  [[assign-y-coords]] and [[assign-node-ids]] accept a mutable atom to
  track traversal state; these functions are not referentially transparent
  unless callers treat the atom as part of the input/output state (e.g. by
  passing a fresh atom when purity is required).

  Scale-related helpers (`calculate-scale-unit`, `get-ticks`) now live
  in [[app.scale]].

  See [[app.specs]] for function specs."
  (:require [app.newick :as newick]))

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
        metadata-index (when (and id-key (seq metadata-rows))
                         (into {} (map (fn [r] [(get r id-key) r]) metadata-rows)))
        enriched-leaves (if metadata-index
                          (mapv #(assoc % :metadata (get metadata-index (:name %)))
                                leaves)
                          leaves)]
    {:tree root
     :tips enriched-leaves
     :max-depth (get-max-x root)}))

;; ===== Spatial Selection =====

(defn leaves-in-rect
  "Returns a set of tip names whose positioned coordinates fall inside a
  bounding rectangle.

  Arguments:
  - `tips`     - positioned leaf nodes (each with `:x`, `:y`, `:name`)
  - `rect`     - map `{:min-x :max-x :min-y :max-y}` in SVG user-space
  - `x-scale`  - horizontal scaling factor (pixels per branch-length unit)
  - `y-mult`   - vertical scaling factor (pixels per tip)
  - `pad-x`    - horizontal padding offset (px)
  - `pad-y`    - vertical padding offset (px)
  - `left-shift` - additional horizontal shift (px)

  Used by box-select / lasso selection in the viewer."
  [tips {:keys [min-x max-x min-y max-y]} x-scale y-mult pad-x pad-y left-shift]
  (into #{}
        (comp
         (filter (fn [tip]
                   (let [lx (+ pad-x left-shift (* (:x tip) x-scale))
                         ly (+ pad-y (* (:y tip) y-mult))]
                     (and (<= min-x lx max-x)
                          (<= min-y ly max-y)))))
         (map :name))
        tips))
