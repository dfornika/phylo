(ns app.color-test
  "Tests for color scale helpers in [[app.color]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.color :as color]))

;; ===== infer-value-type =====

(deftest infer-value-type-numeric
  (testing "Infers numeric type from all numeric values"
    (let [result (color/infer-value-type ["1.5" "2.3" "4.0" "10"])]
      (is (= :numeric (:type result)))
      (is (= [1.5 2.3 4.0 10] (:values result))))))

(deftest infer-value-type-numeric-with-negatives
  (testing "Infers numeric type with negative numbers"
    (let [result (color/infer-value-type ["-5.5" "0" "3.2" "-10.1"])]
      (is (= :numeric (:type result)))
      (is (= [-5.5 0 3.2 -10.1] (:values result))))))

(deftest infer-value-type-date-iso-format
  (testing "Infers date type from ISO date strings (YYYY-MM-DD)"
    (let [result (color/infer-value-type ["2024-01-01" "2024-02-15" "2024-03-20"])]
      (is (= :date (:type result)))
      (is (every? number? (:values result)))
      (is (= 3 (count (:values result)))))))

(deftest infer-value-type-date-slash-format
  (testing "Infers date type from slash-separated date strings"
    (let [result (color/infer-value-type ["01/15/2024" "02/20/2024" "03/25/2024"])]
      (is (= :date (:type result)))
      (is (every? number? (:values result)))
      (is (= 3 (count (:values result)))))))

(deftest infer-value-type-date-mixed-formats
  (testing "Infers date type from mixed date formats"
    (let [result (color/infer-value-type ["2024-01-01" "01/15/2024" "2024-02-20"])]
      (is (= :date (:type result)))
      (is (every? number? (:values result)))
      (is (= 3 (count (:values result)))))))

(deftest infer-value-type-categorical-strings
  (testing "Infers categorical type from non-numeric strings"
    (let [result (color/infer-value-type ["alpha" "beta" "gamma"])]
      (is (= :categorical (:type result)))
      (is (= ["alpha" "beta" "gamma"] (:values result))))))

(deftest infer-value-type-categorical-lineages
  (testing "Infers categorical type from lineage codes"
    (let [result (color/infer-value-type ["B.1.1.7" "P.1" "AY.4"])]
      (is (= :categorical (:type result)))
      (is (= ["B.1.1.7" "P.1" "AY.4"] (:values result))))))

(deftest infer-value-type-hierarchical-codes
  (testing "Treats hierarchical codes (dots with digits) as categorical"
    (let [result (color/infer-value-type ["1.2.3" "2.4.5" "3.6.9"])]
      (is (= :categorical (:type result)))
      (is (= ["1.2.3" "2.4.5" "3.6.9"] (:values result))))))

(deftest infer-value-type-empty-values
  (testing "Returns categorical type for empty collection"
    (let [result (color/infer-value-type [])]
      (is (= :categorical (:type result)))
      (is (= [] (:values result))))))

(deftest infer-value-type-mixed-numeric-strings
  (testing "Falls back to categorical for mixed numeric and non-numeric"
    (let [result (color/infer-value-type ["1.5" "2.3" "foo" "4.0"])]
      (is (= :categorical (:type result)))
      (is (= ["1.5" "2.3" "foo" "4.0"] (:values result))))))

(deftest infer-value-type-filters-blank-values
  (testing "Filters out blank and nil values before type inference"
    (let [result (color/infer-value-type ["1.5" "" "2.3" nil "  " "4.0"])]
      (is (= :numeric (:type result)))
      (is (= [1.5 2.3 4.0] (:values result))))))

(deftest infer-value-type-all-blank-returns-categorical
  (testing "Returns categorical with empty values when all values are blank"
    (let [result (color/infer-value-type ["" nil "  " ""])]
      (is (= :categorical (:type result)))
      (is (= [] (:values result))))))

(deftest infer-value-type-mixed-date-invalid
  (testing "Falls back to categorical when some values are not valid dates"
    (let [result (color/infer-value-type ["2024-01-01" "not-a-date" "2024-03-20"])]
      (is (= :categorical (:type result)))
      (is (= ["2024-01-01" "not-a-date" "2024-03-20"] (:values result))))))

(deftest infer-value-type-numeric-strings-with-spaces
  (testing "Parses numeric values with surrounding whitespace"
    (let [result (color/infer-value-type [" 1.5 " "  2.3" "4.0  "])]
      (is (= :numeric (:type result)))
      (is (= [1.5 2.3 4.0] (:values result))))))

;; ===== build-color-map =====

(deftest build-color-map-numeric-basic
  (testing "Builds color map for numeric field"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}
                {:name "tip3" :metadata {:value "10.0"}}]
          result (color/build-color-map tips :value nil :auto)]
      (is (= 3 (count result)))
      (is (contains? result "tip1"))
      (is (contains? result "tip2"))
      (is (contains? result "tip3"))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

(deftest build-color-map-numeric-with-missing-values
  (testing "Excludes tips with missing numeric values"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value ""}}
                {:name "tip3" :metadata {:value "10.0"}}
                {:name "tip4" :metadata {:value nil}}]
          result (color/build-color-map tips :value nil :auto)]
      (is (= 2 (count result)))
      (is (contains? result "tip1"))
      (is (not (contains? result "tip2")))
      (is (contains? result "tip3"))
      (is (not (contains? result "tip4"))))))

(deftest build-color-map-date-basic
  (testing "Builds color map for date field"
    (let [tips [{:name "tip1" :metadata {:date "2024-01-01"}}
                {:name "tip2" :metadata {:date "2024-06-15"}}
                {:name "tip3" :metadata {:date "2024-12-31"}}]
          result (color/build-color-map tips :date nil :auto)]
      (is (= 3 (count result)))
      (is (contains? result "tip1"))
      (is (contains? result "tip2"))
      (is (contains? result "tip3"))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

(deftest build-color-map-date-with-mixed-formats
  (testing "Builds color map for dates in mixed formats"
    (let [tips [{:name "tip1" :metadata {:date "2024-01-01"}}
                {:name "tip2" :metadata {:date "06/15/2024"}}
                {:name "tip3" :metadata {:date "2024-12-31"}}]
          result (color/build-color-map tips :date nil :auto)]
      (is (= 3 (count result)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

(deftest build-color-map-categorical-basic
  (testing "Builds color map for categorical field"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group "B"}}
                {:name "tip3" :metadata {:group "C"}}
                {:name "tip4" :metadata {:group "A"}}]
          result (color/build-color-map tips :group nil :auto)]
      (is (= 4 (count result)))
      ;; Tips with same group should get same color
      (is (= (get result "tip1") (get result "tip4")))
      ;; Different groups should get different colors
      (is (not= (get result "tip1") (get result "tip2")))
      (is (not= (get result "tip2") (get result "tip3"))))))

(deftest build-color-map-categorical-with-missing-values
  (testing "Excludes tips with missing categorical values"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group ""}}
                {:name "tip3" :metadata {:group "B"}}
                {:name "tip4" :metadata {:group nil}}]
          result (color/build-color-map tips :group nil :auto)]
      (is (= 2 (count result)))
      (is (contains? result "tip1"))
      (is (not (contains? result "tip2")))
      (is (contains? result "tip3"))
      (is (not (contains? result "tip4"))))))

(deftest build-color-map-type-override-numeric
  (testing "Overrides type inference when type-override is :numeric"
    (let [tips [{:name "tip1" :metadata {:value "alpha"}}
                {:name "tip2" :metadata {:value "beta"}}]
          ;; Without override, these would be categorical
          result-auto (color/build-color-map tips :value nil :auto)
          ;; With numeric override, non-numeric values should be excluded
          result-numeric (color/build-color-map tips :value nil :numeric)]
      (is (= 2 (count result-auto)))
      (is (= 0 (count result-numeric))))))

(deftest build-color-map-type-override-categorical
  (testing "Overrides type inference when type-override is :categorical"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "2.0"}}
                {:name "tip3" :metadata {:value "1.0"}}]
          result (color/build-color-map tips :value nil :categorical)]
      (is (= 3 (count result)))
      ;; As categorical, same values should map to same color
      (is (= (get result "tip1") (get result "tip3"))))))

(deftest build-color-map-type-override-date
  (testing "Overrides type inference when type-override is :date"
    (let [tips [{:name "tip1" :metadata {:value "2024-01-01"}}
                {:name "tip2" :metadata {:value "2024-06-15"}}
                {:name "tip3" :metadata {:value "2024-12-31"}}]
          result (color/build-color-map tips :value nil :date)]
      (is (= 3 (count result)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

(deftest build-color-map-palette-id-categorical
  (testing "Uses specified categorical palette"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group "B"}}]
          result-bright (color/build-color-map tips :group :bright :auto)
          result-contrast (color/build-color-map tips :group :contrast :auto)]
      ;; Both should produce color maps
      (is (= 2 (count result-bright)))
      (is (= 2 (count result-contrast)))
      ;; Colors should be different between palettes
      ;; (unless by chance they use the same colors for A and B)
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-bright)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-contrast))))))

(deftest build-color-map-palette-id-gradient
  (testing "Uses specified gradient palette for numeric field"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}]
          result-blue-red (color/build-color-map tips :value :blue-red :auto)
          result-teal-gold (color/build-color-map tips :value :teal-gold :auto)]
      (is (= 2 (count result-blue-red)))
      (is (= 2 (count result-teal-gold)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-blue-red)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-teal-gold))))))

(deftest build-color-map-empty-tips
  (testing "Returns empty map for empty tips collection"
    (let [result (color/build-color-map [] :value nil :auto)]
      (is (= {} result)))))

(deftest build-color-map-missing-field
  (testing "Returns empty map when field is missing from all tips"
    (let [tips [{:name "tip1" :metadata {:other "A"}}
                {:name "tip2" :metadata {:other "B"}}]
          result (color/build-color-map tips :missing-field nil :auto)]
      (is (= {} result)))))

(deftest build-color-map-numeric-single-value
  (testing "Handles single unique numeric value gracefully"
    (let [tips [{:name "tip1" :metadata {:value "5.0"}}
                {:name "tip2" :metadata {:value "5.0"}}]
          result (color/build-color-map tips :value nil :auto)]
      (is (= 2 (count result)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

(deftest build-color-map-categorical-many-values
  (testing "Cycles through palette colors for many categorical values"
    (let [tips (mapv (fn [i] {:name (str "tip" i) :metadata {:group (str "group" i)}})
                     (range 20))
          result (color/build-color-map tips :group nil :auto)]
      (is (= 20 (count result)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result))))))

;; ===== infer-field-type =====

(deftest infer-field-type-numeric
  (testing "Infers numeric field type from metadata rows"
    (let [rows [{:value "1.5"} {:value "2.3"} {:value "4.0"}]]
      (is (= :numeric (color/infer-field-type rows :value))))))

(deftest infer-field-type-categorical
  (testing "Infers categorical field type from metadata rows"
    (let [rows [{:group "A"} {:group "B"} {:group "C"}]]
      (is (= :categorical (color/infer-field-type rows :group))))))

(deftest infer-field-type-date
  (testing "Infers date field type from metadata rows"
    (let [rows [{:date "2024-01-01"} {:date "2024-02-15"} {:date "2024-03-20"}]]
      (is (= :date (color/infer-field-type rows :date))))))

;; ===== resolve-field-type =====

(deftest resolve-field-type-auto
  (testing "Uses inferred type when override is :auto"
    (let [values ["1.0" "2.0" "3.0"]]
      (is (= :numeric (color/resolve-field-type values :auto))))))

(deftest resolve-field-type-override-categorical
  (testing "Uses categorical override regardless of inferred type"
    (let [values ["1.0" "2.0" "3.0"]]
      (is (= :categorical (color/resolve-field-type values :categorical))))))

(deftest resolve-field-type-override-numeric
  (testing "Uses numeric override regardless of inferred type"
    (let [values ["A" "B" "C"]]
      (is (= :numeric (color/resolve-field-type values :numeric))))))

(deftest resolve-field-type-override-date
  (testing "Uses date override regardless of inferred type"
    (let [values ["A" "B" "C"]]
      (is (= :date (color/resolve-field-type values :date))))))

(deftest resolve-field-type-invalid-override-defaults-to-auto
  (testing "Falls back to :auto for invalid override values"
    (let [values ["1.0" "2.0" "3.0"]]
      (is (= :numeric (color/resolve-field-type values :invalid)))
      (is (= :numeric (color/resolve-field-type values nil))))))
