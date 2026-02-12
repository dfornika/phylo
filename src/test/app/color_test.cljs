(ns app.color-test
  "Tests for color scale helpers in [[app.color]]."
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.string :as str]
            [app.color :as color]))

;; ===== categorical-options =====

(deftest categorical-options-returns-ordered-palettes
  (testing "Returns categorical palette options in order"
    (let [options (color/categorical-options)]
      (is (= 3 (count options)))
      (is (= :bright (:id (first options))))
      (is (= :contrast (:id (second options))))
      (is (= :pastel (:id (nth options 2))))
      (is (every? #(contains? % :id) options))
      (is (every? #(contains? % :label) options))
      (is (every? #(contains? % :colors) options)))))

;; ===== gradient-options =====

(deftest gradient-options-returns-ordered-palettes
  (testing "Returns gradient palette options in order"
    (let [options (color/gradient-options)]
      (is (= 2 (count options)))
      (is (= :blue-red (:id (first options))))
      (is (= :teal-gold (:id (second options))))
      (is (every? #(contains? % :id) options))
      (is (every? #(contains? % :label) options))
      (is (every? #(contains? % :colors) options)))))

;; ===== palette-options =====

(deftest palette-options-numeric-returns-gradients
  (testing "Returns gradient palettes for numeric field type"
    (let [options (color/palette-options :numeric)]
      (is (= 2 (count options)))
      (is (= :blue-red (:id (first options)))))))

(deftest palette-options-date-returns-gradients
  (testing "Returns gradient palettes for date field type"
    (let [options (color/palette-options :date)]
      (is (= 2 (count options)))
      (is (= :blue-red (:id (first options)))))))

(deftest palette-options-categorical-returns-categorical
  (testing "Returns categorical palettes for categorical field type"
    (let [options (color/palette-options :categorical)]
      (is (= 3 (count options)))
      (is (= :bright (:id (first options)))))))

(deftest palette-options-string-returns-categorical
  (testing "Returns categorical palettes for string field type"
    (let [options (color/palette-options :string)]
      (is (= 3 (count options)))
      (is (= :bright (:id (first options)))))))

;; ===== resolve-palette =====

(deftest resolve-palette-numeric-with-valid-id
  (testing "Resolves valid gradient palette for numeric type"
    (let [result (color/resolve-palette :numeric :teal-gold)]
      (is (= :teal-gold (:id result)))
      (is (contains? (:palette result) :label))
      (is (contains? (:palette result) :colors)))))

(deftest resolve-palette-numeric-with-invalid-id-uses-default
  (testing "Falls back to default gradient for invalid palette ID"
    (let [result (color/resolve-palette :numeric :invalid-palette)]
      (is (= :blue-red (:id result)))
      (is (contains? (:palette result) :colors)))))

(deftest resolve-palette-categorical-with-valid-id
  (testing "Resolves valid categorical palette for categorical type"
    (let [result (color/resolve-palette :categorical :contrast)]
      (is (= :contrast (:id result)))
      (is (contains? (:palette result) :label))
      (is (contains? (:palette result) :colors)))))

(deftest resolve-palette-categorical-with-invalid-id-uses-default
  (testing "Falls back to default categorical for invalid palette ID"
    (let [result (color/resolve-palette :categorical :invalid-palette)]
      (is (= :bright (:id result)))
      (is (contains? (:palette result) :colors)))))

(deftest resolve-palette-date-uses-gradient
  (testing "Uses gradient palette for date type"
    (let [result (color/resolve-palette :date :blue-red)]
      (is (= :blue-red (:id result)))
      (is (contains? (:palette result) :colors)))))

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
          result-with-auto (color/build-color-map tips :value nil :auto)
          ;; With numeric override, non-numeric values should be excluded
          result-with-numeric (color/build-color-map tips :value nil :numeric)]
      (is (= 2 (count result-with-auto)))
      (is (= 0 (count result-with-numeric))))))

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
          result-with-bright (color/build-color-map tips :group :bright :auto)
          result-with-contrast (color/build-color-map tips :group :contrast :auto)]
      (is (= 2 (count result-with-bright)))
      (is (= 2 (count result-with-contrast)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-with-bright)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-with-contrast))))))

(deftest build-color-map-palette-id-gradient
  (testing "Uses specified gradient palette for numeric field"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}]
          result-with-blue-red (color/build-color-map tips :value :blue-red :auto)
          result-with-teal-gold (color/build-color-map tips :value :teal-gold :auto)]
      (is (= 2 (count result-with-blue-red)))
      (is (= 2 (count result-with-teal-gold)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-with-blue-red)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" %) (vals result-with-teal-gold))))))

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

;; ===== build-legend =====

(deftest build-legend-numeric-basic
  (testing "Builds legend with binned ranges for numeric field"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}
                {:name "tip3" :metadata {:value "10.0"}}]
          result (color/build-legend tips :value nil :auto)]
      (is (= :numeric (:type result)))
      (is (= 5 (count (:entries result))))
      ;; Check each entry has required fields
      (is (every? #(contains? % :id) (:entries result)))
      (is (every? #(contains? % :label) (:entries result)))
      (is (every? #(contains? % :color) (:entries result)))
      ;; Check labels contain ranges
      (is (every? #(re-find #"-" (:label %)) (butlast (:entries result))))
      ;; Check colors are valid hex
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result))))))

(deftest build-legend-numeric-with-missing-values
  (testing "Builds legend excluding missing numeric values"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value ""}}
                {:name "tip3" :metadata {:value "10.0"}}
                {:name "tip4" :metadata {:value nil}}]
          result (color/build-legend tips :value nil :auto)]
      (is (= :numeric (:type result)))
      (is (= 5 (count (:entries result))))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result))))))

(deftest build-legend-numeric-single-value
  (testing "Handles single unique numeric value gracefully"
    (let [tips [{:name "tip1" :metadata {:value "5.0"}}
                {:name "tip2" :metadata {:value "5.0"}}]
          result (color/build-legend tips :value nil :auto)]
      (is (= :numeric (:type result)))
      (is (= 1 (count (:entries result))))
      (let [entry (first (:entries result))]
        (is (not (str/includes? (:label entry) "-")))
        (is (re-matches #"#[0-9a-fA-F]{6}" (:color entry)))))))

(deftest build-legend-empty-data
  (testing "Returns empty entries for field with no valid data"
    (let [tips [{:name "tip1" :metadata {:value ""}}
                {:name "tip2" :metadata {:value nil}}]
          result (color/build-legend tips :value nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 0 (count (:entries result)))))))

(deftest build-legend-date-basic
  (testing "Builds legend with binned date ranges"
    (let [tips [{:name "tip1" :metadata {:date "2024-01-01"}}
                {:name "tip2" :metadata {:date "2024-06-15"}}
                {:name "tip3" :metadata {:date "2024-12-31"}}]
          result (color/build-legend tips :date nil :auto)]
      (is (= :date (:type result)))
      (is (= 5 (count (:entries result))))
      ;; Check each entry has required fields
      (is (every? #(contains? % :id) (:entries result)))
      (is (every? #(contains? % :label) (:entries result)))
      (is (every? #(contains? % :color) (:entries result)))
      ;; Check labels are formatted as dates (YYYY-MM-DD)
      (is (every? #(re-find #"\d{4}-\d{2}-\d{2}" (:label %)) (:entries result)))
      ;; Check colors are valid hex
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result))))))

(deftest build-legend-date-with-mixed-formats
  (testing "Builds legend for dates in mixed formats"
    (let [tips [{:name "tip1" :metadata {:date "2024-01-01"}}
                {:name "tip2" :metadata {:date "06/15/2024"}}
                {:name "tip3" :metadata {:date "2024-12-31"}}]
          result (color/build-legend tips :date nil :auto)]
      (is (= :date (:type result)))
      ;; Should successfully bin all 3 dates
      (is (= 5 (count (:entries result))))
      ;; All labels should be formatted as dates
      (is (every? #(re-find #"\d{4}-\d{2}-\d{2}" (:label %)) (:entries result)))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result))))))

(deftest build-legend-date-single-value
  (testing "Handles single unique date value gracefully"
    (let [tips [{:name "tip1" :metadata {:date "2024-01-01"}}
                {:name "tip2" :metadata {:date "2024-01-01"}}]
          result (color/build-legend tips :date nil :auto)]
      (is (= :date (:type result)))
      (is (= 1 (count (:entries result))))
      (let [entry (first (:entries result))]
        (is (re-find #"\d{4}-\d{2}-\d{2}" (:label entry)))
        (is (re-matches #"#[0-9a-fA-F]{6}" (:color entry)))))))

(deftest build-legend-categorical-basic
  (testing "Builds legend with unique categorical values"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group "B"}}
                {:name "tip3" :metadata {:group "C"}}
                {:name "tip4" :metadata {:group "A"}}]
          result (color/build-legend tips :group nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 3 (count (:entries result))))
      ;; Check each entry has required fields
      (is (every? #(contains? % :id) (:entries result)))
      (is (every? #(contains? % :label) (:entries result)))
      (is (every? #(contains? % :color) (:entries result)))
      ;; Check values are sorted
      (is (= ["A" "B" "C"] (mapv :label (:entries result))))
      ;; Check colors are valid hex
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result)))
      ;; Check each unique value has a different color
      (is (= 3 (count (set (map :color (:entries result)))))))))

(deftest build-legend-categorical-with-missing-values
  (testing "Excludes missing categorical values from legend"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group ""}}
                {:name "tip3" :metadata {:group "B"}}
                {:name "tip4" :metadata {:group nil}}]
          result (color/build-legend tips :group nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 2 (count (:entries result))))
      (is (= ["A" "B"] (mapv :label (:entries result)))))))

(deftest build-legend-categorical-many-values
  (testing "Cycles through palette colors for many categorical values"
    (let [tips (mapv (fn [i] {:name (str "tip" i) :metadata {:group (str "group" i)}})
                     (range 20))
          result (color/build-legend tips :group nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 20 (count (:entries result))))
      (is (every? #(re-matches #"#[0-9a-fA-F]{6}" (:color %)) (:entries result))))))

(deftest build-legend-type-override-numeric
  (testing "Overrides type inference when type-override is :numeric"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}
                {:name "tip3" :metadata {:value "10.0"}}]
          result (color/build-legend tips :value nil :numeric)]
      (is (= :numeric (:type result)))
      (is (= 5 (count (:entries result)))))))

(deftest build-legend-type-override-categorical
  (testing "Overrides type inference when type-override is :categorical"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "2.0"}}
                {:name "tip3" :metadata {:value "1.0"}}]
          result (color/build-legend tips :value nil :categorical)]
      (is (= :categorical (:type result)))
      (is (= 2 (count (:entries result))))
      (is (= ["1.0" "2.0"] (mapv :label (:entries result)))))))

(deftest build-legend-type-override-date
  (testing "Overrides type inference when type-override is :date"
    (let [tips [{:name "tip1" :metadata {:value "2024-01-01"}}
                {:name "tip2" :metadata {:value "2024-06-15"}}
                {:name "tip3" :metadata {:value "2024-12-31"}}]
          result (color/build-legend tips :value nil :date)]
      (is (= :date (:type result)))
      (is (= 5 (count (:entries result)))))))

(deftest build-legend-palette-id-categorical
  (testing "Uses specified categorical palette"
    (let [tips [{:name "tip1" :metadata {:group "A"}}
                {:name "tip2" :metadata {:group "B"}}
                {:name "tip3" :metadata {:group "C"}}]
          result-bright (color/build-legend tips :group :bright :auto)
          result-contrast (color/build-legend tips :group :contrast :auto)
          result-pastel (color/build-legend tips :group :pastel :auto)]
      (is (= :categorical (:type result-bright)))
      (is (= :categorical (:type result-contrast)))
      (is (= :categorical (:type result-pastel)))
      ;; Different palettes should produce different colors
      (is (not= (map :color (:entries result-bright))
                (map :color (:entries result-contrast))))
      (is (not= (map :color (:entries result-bright))
                (map :color (:entries result-pastel)))))))

(deftest build-legend-palette-id-gradient
  (testing "Uses specified gradient palette for numeric field"
    (let [tips [{:name "tip1" :metadata {:value "1.0"}}
                {:name "tip2" :metadata {:value "5.0"}}
                {:name "tip3" :metadata {:value "10.0"}}]
          result-blue-red (color/build-legend tips :value :blue-red :auto)
          result-teal-gold (color/build-legend tips :value :teal-gold :auto)]
      (is (= :numeric (:type result-blue-red)))
      (is (= :numeric (:type result-teal-gold)))
      ;; Different palettes should produce different colors
      (is (not= (map :color (:entries result-blue-red))
                (map :color (:entries result-teal-gold)))))))

(deftest build-legend-empty-tips
  (testing "Returns empty entries for empty tips collection"
    (let [result (color/build-legend [] :value nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 0 (count (:entries result)))))))

(deftest build-legend-missing-field
  (testing "Returns empty entries when field is missing from all tips"
    (let [tips [{:name "tip1" :metadata {:other "A"}}
                {:name "tip2" :metadata {:other "B"}}]
          result (color/build-legend tips :missing-field nil :auto)]
      (is (= :categorical (:type result)))
      (is (= 0 (count (:entries result)))))))

;; ===== trim-trailing-zeros =====

(deftest trim-trailing-zeros-removes-trailing-zeros
  (testing "Removes trailing zeros from numeric strings"
    (is (= "1" (#'color/trim-trailing-zeros "1.000")))
    (is (= "1.5" (#'color/trim-trailing-zeros "1.500")))
    (is (= "1.25" (#'color/trim-trailing-zeros "1.250")))))

(deftest trim-trailing-zeros-removes-decimal-point-if-all-zeros
  (testing "Removes decimal point when all fractional digits are zero"
    (is (= "10" (#'color/trim-trailing-zeros "10.0")))
    (is (= "100" (#'color/trim-trailing-zeros "100.00")))
    (is (= "5" (#'color/trim-trailing-zeros "5.0000")))))

(deftest trim-trailing-zeros-preserves-non-zero-decimals
  (testing "Preserves non-zero decimal digits"
    (is (= "1.23" (#'color/trim-trailing-zeros "1.23")))
    (is (= "0.001" (#'color/trim-trailing-zeros "0.001")))
    (is (= "99.99" (#'color/trim-trailing-zeros "99.99")))))

(deftest trim-trailing-zeros-handles-integers
  (testing "Handles integers without decimal points"
    (is (= "42" (#'color/trim-trailing-zeros "42")))
    (is (= "100" (#'color/trim-trailing-zeros "100")))
    (is (= "0" (#'color/trim-trailing-zeros "0")))))

(deftest trim-trailing-zeros-handles-edge-cases
  (testing "Handles edge cases"
    (is (= "0" (#'color/trim-trailing-zeros "0.0")))
    (is (= "" (#'color/trim-trailing-zeros "")))
    (is (= "1.1" (#'color/trim-trailing-zeros "1.10000")))))

;; ===== format-number =====

(deftest format-number-large-values
  (testing "Formats large values (>= 1000) with no decimal places"
    (is (= "1000" (#'color/format-number 1000)))
    (is (= "5432" (#'color/format-number 5432.789)))
    (is (= "10000" (#'color/format-number 10000.1)))))

(deftest format-number-medium-values
  (testing "Formats medium values (>= 100, < 1000) with 1 decimal place"
    (is (= "100" (#'color/format-number 100.0)))
    (is (= "456.8" (#'color/format-number 456.789)))
    (is (= "999.9" (#'color/format-number 999.99)))))

(deftest format-number-small-values
  (testing "Formats small values (>= 1, < 100) with 2 decimal places"
    (is (= "1" (#'color/format-number 1.0)))
    (is (= "50.25" (#'color/format-number 50.25)))
    (is (= "99.99" (#'color/format-number 99.99)))))

(deftest format-number-tiny-values
  (testing "Formats tiny values (< 1) with 3 decimal places"
    (is (= "0.5" (#'color/format-number 0.5)))
    (is (= "0.123" (#'color/format-number 0.123)))
    (is (= "0.999" (#'color/format-number 0.999)))))

(deftest format-number-negative-values
  (testing "Formats negative values based on absolute value"
    (is (= "-1000" (#'color/format-number -1000)))
    (is (= "-456.8" (#'color/format-number -456.789)))
    (is (= "-50.25" (#'color/format-number -50.25)))
    (is (= "-0.123" (#'color/format-number -0.123)))))

(deftest format-number-trims-trailing-zeros
  (testing "Trims trailing zeros from formatted output"
    (is (= "100" (#'color/format-number 100.0)))
    (is (= "1.5" (#'color/format-number 1.5)))
    (is (= "0.5" (#'color/format-number 0.5)))))

(deftest format-number-zero
  (testing "Formats zero correctly"
    (is (= "0" (#'color/format-number 0)))
    (is (= "0" (#'color/format-number 0.0)))))

;; ===== format-date-ms =====

(deftest format-date-ms-basic-formatting
  (testing "Formats epoch milliseconds to YYYY-MM-DD"
    ;; 2024-01-01 00:00:00 UTC = 1704067200000 ms
    (is (= "2024-01-01" (#'color/format-date-ms 1704067200000)))
    ;; 2024-06-15 00:00:00 UTC = 1718409600000 ms
    (is (= "2024-06-15" (#'color/format-date-ms 1718409600000)))))

(deftest format-date-ms-epoch-zero
  (testing "Formats epoch zero (1970-01-01)"
    ;; Unix epoch = 0 ms = 1970-01-01 00:00:00 UTC
    (is (= "1970-01-01" (#'color/format-date-ms 0)))))

(deftest format-date-ms-pads-month-and-day
  (testing "Pads month and day with leading zeros"
    ;; 2024-01-05 00:00:00 UTC = 1704412800000 ms
    (is (= "2024-01-05" (#'color/format-date-ms 1704412800000)))
    ;; 2024-09-03 00:00:00 UTC = 1725321600000 ms
    (is (= "2024-09-03" (#'color/format-date-ms 1725321600000)))))

(deftest format-date-ms-end-of-year
  (testing "Formats end-of-year dates correctly"
    ;; 2024-12-31 00:00:00 UTC = 1735603200000 ms
    (is (= "2024-12-31" (#'color/format-date-ms 1735603200000)))))

(deftest format-date-ms-different-years
  (testing "Formats dates from different years"
    ;; 2020-03-15 00:00:00 UTC = 1584230400000 ms
    (is (= "2020-03-15" (#'color/format-date-ms 1584230400000)))
    ;; 2025-07-04 00:00:00 UTC = 1751587200000 ms
    (is (= "2025-07-04" (#'color/format-date-ms 1751587200000)))))
