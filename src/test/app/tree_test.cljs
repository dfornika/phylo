(ns app.tree-test
  "Tests for tree layout and utility functions in [[app.tree]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.tree :as tree]
            [app.newick :as newick]))

;; ===== Helper: build a positioned tree from a Newick string =====

(defn positioned-tree
  "Parses a Newick string and assigns x/y coordinates."
  [newick-str]
  (-> (newick/newick->map newick-str)
      (tree/assign-y-coords (atom 0))
      first
      tree/assign-x-coords))

;; ===== count-tips =====

(deftest count-tips-single-leaf
  (testing "A single leaf counts as 1 tip"
    (is (= 1 (tree/count-tips {:name "A" :branch-length 1.0 :children []})))))

(deftest count-tips-two-leaf-tree
  (testing "A two-leaf tree has 2 tips"
    (let [tree (newick/newick->map "(A:0.1,B:0.2)Root:0.3;")]
      (is (= 2 (tree/count-tips tree))))))

(def ^:private abc-tree-str
  "Local copy of the 25-taxon sample tree for testing."
  "(((A:1.575,B:1.575)C:5.99484,((D:5.1375,(E:4.21625,(F:1.32,(G:0.525,H:0.525)I:0.795)J:2.89625)K:0.92125)L:1.5993,((M:2.895,(N:2.11,O:2.11)P:0.785)Q:3.1725,R:6.0675)S:0.6693)T:1.50234)U:2.86223,((V:1.58,(W:1.055,X:1.055)Y:0.525)Z:5.17966,(AA:4.60414,(AB:2.95656,((AC:1.8425,(AD:0.525,AE:0.525)AF:1.3175)AG:0.99844,((AH:1.1975,(AI:1.055,(AJ:0,AK:0)AL:1.055)AM:0.1425)AN:0.92281,(AO:1.58,AP:1.58)AQ:0.54031)AR:1.26094)AS:1.11406)AT:1.64758)AU:2.15552)AV:4.32559)AW:10.4109")

(deftest count-tips-abc-tree
  (testing "abc-tree has 25 tips"
    (let [tree (newick/newick->map abc-tree-str)]
      (is (= 25 (tree/count-tips tree))))))

;; ===== get-max-x =====

(deftest get-max-x-simple-tree
  (testing "max-x of a positioned two-leaf tree"
    (let [t (positioned-tree "(A:0.1,B:0.2)Root:0.3;")]
      (is (> (tree/get-max-x t) 0))
      ;; B has the longer path: 0 + 0.2 = 0.2 (root length ignored)
      (is (== 0.2 (tree/get-max-x t))))))

(deftest get-max-x-deeper-tree
  (testing "max-x on a deeper positioned tree"
    (let [t (positioned-tree "(A:1.0,(B:0.5,C:2.0):1.0):0.0;")]
      ;; Deepest path: 0 + 1.0 + 2.0 = 3.0
      (is (== 3.0 (tree/get-max-x t))))))

;; ===== assign-y-coords =====

(deftest assign-y-coords-sequential-leaves
  (testing "Leaves get sequential integer y-values"
    (let [t (newick/newick->map "(A:0.1,B:0.2,C:0.3)Root;")
          [positioned _] (tree/assign-y-coords t (atom 0))
          leaves (tree/get-leaves positioned)]
      ;; After assign-y + get-leaves, y-values should be 0, 1, 2
      (is (= [0 1 2] (mapv :y leaves))))))

(deftest assign-y-coords-internal-node-average
  (testing "Internal node y is the average of first and last child"
    (let [t (newick/newick->map "(A:0.1,B:0.2)Root;")
          [positioned _] (tree/assign-y-coords t (atom 0))]
      ;; A gets y=0, B gets y=1, Root gets (0+1)/2 = 0.5
      (is (= 0.5 (:y positioned))))))

;; ===== assign-x-coords =====

(deftest assign-x-coords-root-at-zero
  (testing "Root is always positioned at x=0"
    (let [t (positioned-tree "(A:0.1,B:0.2)Root:999;")]
      ;; Root branch-length should be ignored
      (is (== 0 (:x t))))))

(deftest assign-x-coords-children-accumulate-length
  (testing "Children accumulate parent x + own branch-length"
    (let [t (positioned-tree "(A:0.5,B:1.0)Root:0.3;")]
      (is (== 0.5 (-> t :children first :x)))
      (is (== 1.0 (-> t :children second :x))))))

(deftest get-leaves-all-tips
  (testing "get-leaves returns only leaf nodes"
    (let [t (positioned-tree "(A:0.1,(B:0.2,C:0.3)D:0.4)E:0.5;")
          leaves (tree/get-leaves t)]
      (is (= 3 (count leaves)))
      (is (every? #(empty? (:children %)) leaves)))))

(deftest get-leaves-preserves-order
  (testing "Leaves are in left-to-right order"
    (let [t (positioned-tree "(A:0.1,(B:0.2,C:0.3)D:0.4)E:0.5;")
          names (mapv :name (tree/get-leaves t))]
      (is (= ["A" "B" "C"] names)))))

(deftest get-leaves-single-node
  (testing "A single leaf returns itself"
    (let [leaf {:name "X" :branch-length 1.0 :children [] :x 0 :y 0}]
      (is (= [leaf] (tree/get-leaves leaf))))))

;; ===== prepare-tree =====

(deftest prepare-tree-returns-expected-keys
  (testing "prepare-tree returns :tree, :tips, and :max-depth"
    (let [result (tree/prepare-tree "(A:0.1,B:0.2)Root:0.3;" [] [])]
      (is (contains? result :tree))
      (is (contains? result :tips))
      (is (contains? result :max-depth)))))

(deftest prepare-tree-tips-match-leaves
  (testing "Tips are the leaf nodes of the prepared tree"
    (let [{:keys [tips]} (tree/prepare-tree "(A:0.1,(B:0.2,C:0.3):0.4)Root;" [] [])]
      (is (= 3 (count tips)))
      (is (= ["A" "B" "C"] (mapv :name tips))))))

(deftest prepare-tree-max-depth-positive
  (testing "max-depth is positive for a tree with branch lengths"
    (let [{:keys [max-depth]} (tree/prepare-tree "(A:0.1,B:0.2)Root:0.3;" [] [])]
      (is (pos? max-depth)))))

(deftest prepare-tree-merges-metadata
  (testing "Metadata is merged into leaf nodes by first-column ID"
    (let [cols [{:key :id :label "ID" :width 120}
                {:key :color :label "Color" :width 120}]
          rows [{:id "A" :color "red"}
                {:id "B" :color "blue"}]
          {:keys [tips]} (tree/prepare-tree "(A:0.1,B:0.2)Root;" rows cols)]
      (is (= "red" (get-in (first tips) [:metadata :color])))
      (is (= "blue" (get-in (second tips) [:metadata :color]))))))

;; ===== assign-node-ids =====

(deftest assign-node-ids-single-node
  (testing "Single node gets ID 0"
    (let [node {:name "A" :branch-length 1.0 :children []}
          result (tree/assign-node-ids node)]
      (is (= 0 (:id result))))))

(deftest assign-node-ids-all-nodes-have-ids
  (testing "Every node in the tree gets a unique ID"
    (let [t (newick/newick->map "(A:0.1,(B:0.2,C:0.3)D:0.4)E:0.5;")
          result (tree/assign-node-ids t)]
      (letfn [(check-ids [node]
                (is (contains? node :id))
                (is (number? (:id node)))
                (doseq [child (:children node)]
                  (check-ids child)))]
        (check-ids result)))))

(deftest assign-node-ids-unique
  (testing "All IDs in the tree are unique"
    (let [t (newick/newick->map abc-tree-str)
          result (tree/assign-node-ids t)
          all-ids (atom [])]
      (letfn [(collect-ids [node]
                (swap! all-ids conj (:id node))
                (doseq [child (:children node)]
                  (collect-ids child)))]
        (collect-ids result)
        (is (= (count @all-ids) (count (distinct @all-ids))))))))

(deftest assign-node-ids-includes-nil-named-nodes
  (testing "Nodes with nil names still get unique IDs"
    (let [t (newick/newick->map "(A,B);")  ; Root and potentially internal nodes may have nil names
          result (tree/assign-node-ids t)]
      ;; Root should have an ID even if name is nil
      (is (number? (:id result)))
      ;; All children should have IDs
      (is (every? #(number? (:id %)) (:children result))))))

;; ===== reroot-on-branch =====

(defn- id-tree
  "Parses a Newick string and assigns node IDs (but not x/y coords).
  Suitable for testing structural transformations that need :id keys."
  [newick-str]
  (tree/assign-node-ids (newick/newick->map newick-str)))

(defn- collect-all-nodes
  "Collects all nodes in a tree into a flat vector (depth-first)."
  [node]
  (into [node] (mapcat collect-all-nodes (:children node))))

(defn- find-node-by-name
  "Finds the first node with the given name in a tree."
  [node name]
  (first (filter #(= (:name %) name) (collect-all-nodes node))))

(defn- pairwise-distance
  "Returns the distance between two leaf names in a tree.
  Computes root-to-leaf distances and uses: d(A,B) = d(root,A) + d(root,B) - 2*d(root,LCA).
  For simplicity, sums root-to-leaf for each, then subtracts 2x the shared prefix."
  [tree leaf-a leaf-b]
  (letfn [(root-to-leaf-path [node target-name]
            (cond
              (= (:name node) target-name)
              [node]
              (empty? (:children node))
              nil
              :else
              (some (fn [child]
                      (when-let [p (root-to-leaf-path child target-name)]
                        (into [node] p)))
                    (:children node))))
          (path-length [path]
            ;; Sum of branch-lengths, skipping the root (no incoming edge)
            (reduce + 0 (map #(or (:branch-length %) 0) (rest path))))]
    (let [path-a (root-to-leaf-path tree leaf-a)
          path-b (root-to-leaf-path tree leaf-b)
          len-a (path-length path-a)
          len-b (path-length path-b)
          ;; Find LCA depth: length of shared prefix path
          shared (count (take-while (fn [[a b]] (= (:name a) (:name b)))
                                    (map vector path-a path-b)))
          lca-depth (path-length (take shared path-a))]
      (+ (- len-a lca-depth) (- len-b lca-depth)))))

(deftest reroot-on-branch-returns-nil-for-root
  (testing "Rerooting on the root node returns nil (root has no incoming branch)"
    (let [t (id-tree "(A:0.1,B:0.2)Root:0.3;")]
      (is (nil? (tree/reroot-on-branch t (:id t)))))))

(deftest reroot-on-branch-returns-nil-for-missing-id
  (testing "Rerooting with a non-existent ID returns nil"
    (let [t (id-tree "(A:0.1,B:0.2)Root:0.3;")]
      (is (nil? (tree/reroot-on-branch t 9999))))))

(deftest reroot-on-branch-simple-two-leaf
  (testing "Rerooting a 2-leaf tree on a leaf branch"
    (let [t (id-tree "(A:0.1,B:0.2)Root:0.3;")
          a-node (find-node-by-name t "A")
          rerooted (tree/reroot-on-branch t (:id a-node))]
      ;; Should produce a valid tree
      (is (some? rerooted))
      ;; New root has nil name and 2 children
      (is (nil? (:name rerooted)))
      (is (= 2 (count (:children rerooted))))
      ;; Same number of leaves
      (is (= 2 (tree/count-tips rerooted)))
      ;; Leaf names preserved
      (is (= #{"A" "B"} (set (map :name (tree/get-leaves rerooted)))))
      ;; No :id keys remain (they should be stripped)
      (is (every? #(not (contains? % :id)) (collect-all-nodes rerooted))))))

(deftest reroot-on-branch-preserves-pairwise-distances
  (testing "Pairwise tip distances are preserved after rerooting"
    (let [nwk "(A:1.0,(B:2.0,(C:3.0,D:4.0)E:5.0)F:6.0)Root;"
          raw (newick/newick->map nwk)
          t (tree/assign-node-ids raw)
          ;; Get all leaf pairs
          leaves ["A" "B" "C" "D"]
          pairs (for [a leaves b leaves :when (pos? (compare a b))] [a b])
          ;; Record original distances
          orig-distances (into {} (map (fn [[a b]] [[a b] (pairwise-distance t a b)]) pairs))
          ;; Reroot on the branch leading to node B
          b-node (find-node-by-name t "B")
          rerooted (tree/reroot-on-branch t (:id b-node))
          ;; Record rerooted distances
          new-distances (into {} (map (fn [[a b]] [[a b] (pairwise-distance rerooted a b)]) pairs))]
      (doseq [pair (keys orig-distances)]
        ;; Use a small epsilon for floating-point comparison
        (is (< (js/Math.abs (- (orig-distances pair) (new-distances pair))) 1e-10)
            (str "Distance mismatch for " pair
                 ": orig=" (orig-distances pair)
                 " new=" (new-distances pair)))))))

(deftest reroot-on-branch-three-leaf-on-leaf
  (testing "Rerooting a 3-leaf tree on a leaf branch produces correct topology"
    (let [nwk "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;"
          t (id-tree nwk)
          ;; Reroot on A's branch
          a-node (find-node-by-name t "A")
          rerooted (tree/reroot-on-branch t (:id a-node))]
      (is (some? rerooted))
      (is (= 3 (tree/count-tips rerooted)))
      ;; A's branch was 1.0, split in half → A gets 0.5 from new root
      (let [a-child (first (filter #(= "A" (:name %)) (:children rerooted)))]
        (is (some? a-child))
        (is (== 0.5 (:branch-length a-child)))))))

(deftest reroot-on-branch-internal-node
  (testing "Rerooting on an internal node's branch works correctly"
    (let [nwk "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;"
          t (id-tree nwk)
          d-node (find-node-by-name t "D")
          rerooted (tree/reroot-on-branch t (:id d-node))]
      (is (some? rerooted))
      (is (= 3 (tree/count-tips rerooted)))
      (is (= #{"A" "B" "C"} (set (map :name (tree/get-leaves rerooted))))))))

(deftest reroot-on-branch-zero-length
  (testing "Rerooting on a branch with zero/nil length uses 0 for both halves"
    (let [nwk "(A:0.0,B:0.2)Root;"
          t (id-tree nwk)
          a-node (find-node-by-name t "A")
          rerooted (tree/reroot-on-branch t (:id a-node))]
      (is (some? rerooted))
      ;; Both children of the new root should have branch-length 0
      (let [a-child (first (filter #(= "A" (:name %)) (:children rerooted)))]
        (is (== 0 (:branch-length a-child)))))))

(deftest reroot-on-branch-unifurcation-collapse
  (testing "Old bifurcating root collapses into a unifurcation correctly"
    ;; When we reroot on A: the old root had 2 children (A and D).
    ;; We remove A from the path, leaving the old root with just D.
    ;; That's a unifurcation that should be collapsed.
    (let [nwk "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;"
          t (id-tree nwk)
          a-node (find-node-by-name t "A")
          rerooted (tree/reroot-on-branch t (:id a-node))]
      ;; After rerooting on A, the rootward side should NOT have
      ;; a single-child internal node at the old root position.
      ;; It should be collapsed: Root(BL=1.0) with child D(BL=4.0)
      ;; becomes D with BL=1.0+4.0=5.0 (since old root had BL from
      ;; the reversed A→Root edge which is 1.0)
      (let [rootward-child (first (filter #(not= "A" (:name %)) (:children rerooted)))
            all-nodes (collect-all-nodes rootward-child)]
        ;; No unifurcations: every internal node has 2+ children
        (doseq [n all-nodes]
          (when (seq (:children n))
            (is (>= (count (:children n)) 2)
                (str "Unifurcation found at node: " (:name n)))))))))

(deftest reroot-on-branch-round-trip-position
  (testing "Rerooted tree can be positioned successfully"
    (let [nwk "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;"
          t (id-tree nwk)
          b-node (find-node-by-name t "B")
          rerooted (tree/reroot-on-branch t (:id b-node))
          ;; Position the rerooted tree
          positioned (tree/position-tree rerooted)]
      (is (some? (:tree positioned)))
      (is (= 3 (count (:tips positioned))))
      (is (pos? (:max-depth positioned))))))

(deftest reroot-on-branch-newick-round-trip
  (testing "Rerooted tree serializes to Newick and parses back"
    (let [nwk "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;"
          t (id-tree nwk)
          b-node (find-node-by-name t "B")
          rerooted (tree/reroot-on-branch t (:id b-node))
          ;; Serialize and re-parse
          rerooted-nwk (newick/map->newick rerooted)
          reparsed (newick/newick->map rerooted-nwk)]
      (is (string? rerooted-nwk))
      (is (some? reparsed))
      (is (= 3 (tree/count-tips reparsed)))
      (is (= #{"A" "B" "C"} (set (map :name (tree/get-leaves reparsed))))))))

;; ===== ladderize =====

(deftest ladderize-single-leaf-unchanged
  (testing "A single leaf has no children to sort and is returned unchanged"
    (let [leaf {:name "A" :branch-length 1.0 :children []}]
      (is (= leaf (tree/ladderize leaf)))
      (is (= leaf (tree/ladderize leaf :ascending)))
      (is (= leaf (tree/ladderize leaf :descending))))))

(deftest ladderize-ascending-larger-clade-first
  (testing "Ascending ladderize places the larger clade as the first child"
    ;; Tree has 2 children at root: A (1 tip) and D=(B,C) (2 tips).
    ;; Ascending means larger clade first, so D should be first.
    (let [t (newick/newick->map "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;")
          ladderized (tree/ladderize t :ascending)
          [first-child second-child] (:children ladderized)]
      (is (>= (tree/count-tips first-child) (tree/count-tips second-child))))))

(deftest ladderize-descending-smaller-clade-first
  (testing "Descending ladderize places the smaller clade as the first child"
    ;; Descending means smaller clade first, so A (1 tip) should be first.
    (let [t (newick/newick->map "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;")
          ladderized (tree/ladderize t :descending)
          [first-child second-child] (:children ladderized)]
      (is (<= (tree/count-tips first-child) (tree/count-tips second-child))))))

(deftest ladderize-default-direction-is-ascending
  (testing "Calling ladderize with no direction argument is equivalent to :ascending"
    (let [t (newick/newick->map "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;")]
      (is (= (tree/ladderize t) (tree/ladderize t :ascending))))))

(deftest ladderize-preserves-tip-count
  (testing "Ladderize does not add or remove leaves"
    (let [t (newick/newick->map abc-tree-str)]
      (is (= 25 (tree/count-tips (tree/ladderize t :ascending))))
      (is (= 25 (tree/count-tips (tree/ladderize t :descending)))))))

(deftest ladderize-preserves-leaf-names
  (testing "Ladderize preserves the complete set of leaf names"
    (let [t (newick/newick->map "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;")
          orig-names (set (map :name (tree/get-leaves t)))]
      (is (= orig-names (set (map :name (tree/get-leaves (tree/ladderize t :ascending))))))
      (is (= orig-names (set (map :name (tree/get-leaves (tree/ladderize t :descending)))))))))

(deftest ladderize-idempotent
  (testing "Applying ladderize twice in the same direction yields the same result"
    (let [t (newick/newick->map abc-tree-str)
          once-asc (tree/ladderize t :ascending)
          twice-asc (tree/ladderize once-asc :ascending)
          once-desc (tree/ladderize t :descending)
          twice-desc (tree/ladderize once-desc :descending)]
      (is (= once-asc twice-asc))
      (is (= once-desc twice-desc)))))

(deftest ladderize-newick-round-trip
  (testing "Ladderized tree serializes to Newick and parses back with same tips"
    (let [t (newick/newick->map "(A:1.0,(B:2.0,C:3.0)D:4.0)Root;")
          ladderized (tree/ladderize t)
          nwk (newick/map->newick ladderized)
          reparsed (newick/newick->map nwk)]
      (is (string? nwk))
      (is (some? reparsed))
      (is (= 3 (tree/count-tips reparsed)))
      (is (= #{"A" "B" "C"} (set (map :name (tree/get-leaves reparsed))))))))
