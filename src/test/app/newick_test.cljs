(ns app.newick-test
  "Tests for the Newick parser in [[app.newick]]."
  (:require [cljs.test :refer [deftest testing is are]]
            [app.newick :as newick]))

;; ===== Basic parsing =====

(deftest parse-simple-leaf
  (testing "Single leaf with branch length"
    (let [result (newick/newick->map "A:0.1;")]
      (is (= "A" (:name result)))
      (is (= 0.1 (:branch-length result)))
      (is (= [] (:children result))))))

(deftest parse-simple-leaf-no-length
  (testing "Single leaf without branch length"
    (let [result (newick/newick->map "A;")]
      (is (= "A" (:name result)))
      (is (= [] (:children result))))))

(deftest parse-two-taxon-tree
  (testing "Two-taxon tree: (A,B);"
    (let [result (newick/newick->map "(A,B);")]
      (is (= 2 (count (:children result))))
      (is (= "A" (-> result :children first :name)))
      (is (= "B" (-> result :children second :name))))))

(deftest parse-tree-with-branch-lengths
  (testing "Tree with branch lengths: (A:0.1,B:0.2)Root:0.3;"
    (let [result (newick/newick->map "(A:0.1,B:0.2)Root:0.3;")]
      (is (= "Root" (:name result)))
      (is (= 0.3 (:branch-length result)))
      (is (= 2 (count (:children result))))
      (is (= 0.1 (-> result :children first :branch-length)))
      (is (= 0.2 (-> result :children second :branch-length))))))

(deftest parse-nested-tree
  (testing "Nested tree: ((A,B)C,(D,E)F)Root;"
    (let [result (newick/newick->map "((A,B)C,(D,E)F)Root;")]
      (is (= "Root" (:name result)))
      (is (= 2 (count (:children result))))
      (is (= "C" (-> result :children first :name)))
      (is (= "F" (-> result :children second :name)))
      (is (= 2 (-> result :children first :children count)))
      (is (= 2 (-> result :children second :children count))))))

;; ===== Edge cases =====

(deftest parse-nil-input
  (testing "nil input returns a node without throwing"
    (let [result (newick/newick->map nil)]
      (is (map? result))
      (is (contains? result :name)))))

(deftest parse-empty-input
  (testing "Empty string returns a node without throwing"
    (let [result (newick/newick->map "")]
      (is (map? result))
      (is (contains? result :name)))))

(deftest parse-without-semicolon
  (testing "Tree without trailing semicolon still parses"
    (let [result (newick/newick->map "(A,B)")]
      (is (= 2 (count (:children result)))))))

;; ===== Output structure =====

(deftest output-always-has-required-keys
  (testing "Every node in parsed tree has :name, :branch-length, :children"
    (let [tree (newick/newick->map "(A:0.1,(B:0.2,C:0.3)D:0.4)E:0.5;")]
      (letfn [(check-node [node]
                (is (contains? node :name))
                (is (contains? node :branch-length))
                (is (contains? node :children))
                (is (vector? (:children node)))
                (doseq [child (:children node)]
                  (check-node child)))]
        (check-node tree)))))

;; ===== Full abc-tree =====

(def abc-tree-str
  "(((A:1.575,B:1.575)C:5.99484,((D:5.1375,(E:4.21625,(F:1.32,(G:0.525,H:0.525)I:0.795)J:2.89625)K:0.92125)L:1.5993,((M:2.895,(N:2.11,O:2.11)P:0.785)Q:3.1725,R:6.0675)S:0.6693)T:1.50234)U:2.86223,((V:1.58,(W:1.055,X:1.055)Y:0.525)Z:5.17966,(AA:4.60414,(AB:2.95656,((AC:1.8425,(AD:0.525,AE:0.525)AF:1.3175)AG:0.99844,((AH:1.1975,(AI:1.055,(AJ:0,AK:0)AL:1.055)AM:0.1425)AN:0.92281,(AO:1.58,AP:1.58)AQ:0.54031)AR:1.26094)AS:1.11406)AT:1.64758)AU:2.15552)AV:4.32559)AW:10.4109")

(deftest parse-large-tree-tip-count
  (testing "abc-tree has 25 leaf nodes"
    (let [tree (newick/newick->map abc-tree-str)
          tip-count (letfn [(count-tips [n]
                              (if (empty? (:children n)) 1
                                  (reduce + (map count-tips (:children n)))))]
                      (count-tips tree))]
      (is (= 25 tip-count)))))
