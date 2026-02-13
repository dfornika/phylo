(ns app.specs-test
  "Tests for spec utility functions and generator integration in [[app.specs]]."
  (:require [cljs.test :refer [deftest testing is]]
            [cljs.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [app.specs :as specs]
            ;; Load custom generators so s/exercise works
            [app.spec-generators]))

;; ===== get-allowed-keys =====

(deftest get-allowed-keys-with-req-un-only
  (testing "Returns keys from spec with only required keys"
    (let [allowed (specs/get-allowed-keys ::specs/tree-node)]
      (is (set? allowed))
      (is (contains? allowed :name))
      (is (contains? allowed :branch-length))
      (is (contains? allowed :children))
      (is (= 3 (count allowed))))))

(deftest get-allowed-keys-with-req-and-opt-un
  (testing "Returns keys from spec with both required and optional keys"
    (let [allowed (specs/get-allowed-keys ::specs/metadata-header)]
      (is (set? allowed))
      ;; Required keys
      (is (contains? allowed :key))
      (is (contains? allowed :label))
      (is (contains? allowed :width))
      ;; Optional keys
      (is (contains? allowed :column-type))
      (is (contains? allowed :spacing))
      (is (= 5 (count allowed))))))

(deftest get-allowed-keys-with-positioned-node
  (testing "Returns all keys including x, y, and id for positioned nodes"
    (let [allowed (specs/get-allowed-keys ::specs/positioned-node)]
      (is (set? allowed))
      (is (contains? allowed :name))
      (is (contains? allowed :branch-length))
      (is (contains? allowed :children))
      (is (contains? allowed :x))
      (is (contains? allowed :y))
      (is (contains? allowed :id))
      (is (contains? allowed :leaf-names))
      (is (= 7 (count allowed))))))

(deftest get-allowed-keys-with-parsed-metadata
  (testing "Returns keys from nested spec structure"
    (let [allowed (specs/get-allowed-keys ::specs/parsed-metadata)]
      (is (set? allowed))
      (is (contains? allowed :headers))
      (is (contains? allowed :data))
      (is (= 2 (count allowed))))))

;; ===== validate-spec! =====

(deftest validate-spec-returns-value-unchanged
  (testing "Returns the input value unchanged for valid data"
    (let [input {:name "test" :branch-length 1.5 :children []}
          result (specs/validate-spec! input ::specs/tree-node "test-node")]
      (is (= input result)))))

(deftest validate-spec-returns-value-for-valid-metadata-header
  (testing "Returns value unchanged for valid metadata header"
    (let [input {:key :sample-id :label "Sample ID" :width 120}
          result (specs/validate-spec! input ::specs/metadata-header "header")]
      (is (= input result)))))

(deftest validate-spec-returns-value-for-valid-positioned-node
  (testing "Returns value unchanged for valid positioned node"
    (let [input {:name "leaf1"
                 :branch-length 0.5
                 :children []
                 :x 10.0
                 :y 5.0
                 :id 0}
          result (specs/validate-spec! input ::specs/positioned-node "node")]
      (is (= input result)))))

(deftest validate-spec-with-optional-keys
  (testing "Returns value with optional keys present"
    (let [input {:key :date :label "Collection Date" :width 150 :column-type :date}
          result (specs/validate-spec! input ::specs/metadata-header "header")]
      (is (= input result))
      (is (= :date (:column-type result))))))

(deftest validate-spec-threading-behavior
  (testing "Can be used in threading macros"
    (let [input {:name "node" :branch-length 1.0 :children []}
          result (-> input
                     (specs/validate-spec! ::specs/tree-node "step1")
                     (assoc :extra-field "value"))]
      (is (= "node" (:name result)))
      (is (= "value" (:extra-field result))))))

(deftest validate-spec-with-check-unexpected-keys-disabled
  (testing "Does not check for unexpected keys when option is false"
    (let [input {:name "test" :branch-length 1.0 :children [] :unexpected-key "value"}
          result (specs/validate-spec! input ::specs/tree-node "node" {:check-unexpected-keys? false})]
      ;; Should still return the value (including unexpected key)
      (is (= input result))
      (is (= "value" (:unexpected-key result))))))

(deftest validate-spec-with-nil-value
  (testing "Handles nil values appropriately"
    (let [input nil
          result (specs/validate-spec! input (s/nilable ::specs/tree-node) "nullable-node")]
      (is (nil? result)))))

(deftest validate-spec-with-default-options
  (testing "Uses default options when none provided (3-arg form)"
    (let [input {:key :id :label "ID" :width 100}
          result (specs/validate-spec! input ::specs/metadata-header "header")]
      (is (= input result)))))

;; ===== s/exercise — generator smoke tests =====
;; Verify that the custom generators attached to specs produce
;; values that conform to those specs.

(deftest exercise-tree-node
  (testing "s/exercise ::tree-node generates valid trees"
    (let [samples (s/exercise ::specs/tree-node 10)]
      (is (= 10 (count samples)))
      (doseq [[value conformed] samples]
        (is (s/valid? ::specs/tree-node value)
            (str "Generated tree-node failed validation: " (pr-str value)))))))

(deftest exercise-positioned-node
  (testing "s/exercise ::positioned-node generates valid positioned nodes"
    (let [samples (s/exercise ::specs/positioned-node 10)]
      (is (= 10 (count samples)))
      (doseq [[value _] samples]
        (is (s/valid? ::specs/positioned-node value))
        (is (contains? value :x))
        (is (contains? value :y))
        (is (contains? value :id))))))

(deftest exercise-metadata-header
  (testing "s/exercise ::metadata-header generates valid headers"
    (let [samples (s/exercise ::specs/metadata-header 10)]
      (is (= 10 (count samples)))
      (doseq [[value _] samples]
        (is (s/valid? ::specs/metadata-header value))
        (is (keyword? (:key value)))
        (is (string? (:label value)))
        (is (pos? (:width value)))))))

(deftest exercise-metadata-row
  (testing "s/exercise ::metadata-row generates valid rows"
    (let [samples (s/exercise ::specs/metadata-row 10)]
      (is (= 10 (count samples)))
      (doseq [[value _] samples]
        (is (s/valid? ::specs/metadata-row value))
        (is (every? keyword? (keys value)))
        (is (every? string? (vals value)))))))

(deftest exercise-column-type
  (testing "s/exercise ::column-type generates valid types"
    (let [samples (s/exercise ::specs/column-type 10)]
      (is (= 10 (count samples)))
      (doseq [[value _] samples]
        (is (#{:date :numeric :string} value))))))
