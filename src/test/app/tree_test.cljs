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

;; ===== calculate-scale-unit =====

(deftest calculate-scale-unit-small-values
  (testing "Scale unit for small max-x values"
    ;; 0.37 has magnitude 0.1, ratio 3.7 -> 50% of magnitude -> 0.05
    (let [unit (tree/calculate-scale-unit 0.37)]
      (is (pos? unit))
      (is (< unit 0.37)))))

(deftest calculate-scale-unit-larger-values
  (testing "Scale unit for larger values"
    (let [unit (tree/calculate-scale-unit 5.2)]
      ;; 5.2 has magnitude 1, ratio 5.2 -> full magnitude -> 1
      (is (== 1 unit)))))

(deftest calculate-scale-unit-very-small
  (testing "Scale unit for very small max-x"
    (let [unit (tree/calculate-scale-unit 0.012)]
      (is (pos? unit))
      (is (< unit 0.012)))))

;; ===== get-ticks =====

(deftest get-ticks-basic
  (testing "Ticks from 0 to max-x in increments of unit"
    (let [ticks (tree/get-ticks 1.0 0.25)]
      (is (= [0 0.25 0.5 0.75 1.0] ticks)))))

(deftest get-ticks-doesnt-exceed-max
  (testing "Ticks never exceed max-x"
    (let [ticks (tree/get-ticks 0.9 0.5)]
      (is (every? #(<= % 0.9) ticks))
      (is (= [0 0.5] ticks)))))

(deftest get-ticks-starts-at-zero
  (testing "Ticks always start at 0"
    (is (= 0 (first (tree/get-ticks 10 1))))))

;; ===== get-leaves =====

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
