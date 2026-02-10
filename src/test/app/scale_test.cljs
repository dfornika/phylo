(ns app.scale-test
  "Tests for scale tick calculation functions in [[app.components.scale]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.components.scale :as scale]
            [app.tree :as tree]
            [clojure.string]))

;; ===== tick-position =====

(deftest tick-position-tips-origin
  (testing "tick-position with :tips origin subtracts label from max-depth"
    (is (= 10 (scale/tick-position :tips 10 0)))
    (is (= 7.5 (scale/tick-position :tips 10 2.5)))
    (is (= 0 (scale/tick-position :tips 10 10)))))

(deftest tick-position-root-origin
  (testing "tick-position with :root origin returns label as-is"
    (is (= 0 (scale/tick-position :root 10 0)))
    (is (= 2.5 (scale/tick-position :root 10 2.5)))
    (is (= 10 (scale/tick-position :root 10 10)))))

(deftest tick-position-nil-origin-defaults-to-tips
  (testing "tick-position with nil origin defaults to :tips"
    (is (= 10 (scale/tick-position nil 10 0)))
    (is (= 0 (scale/tick-position nil 10 10)))))

(deftest tick-position-nil-max-depth-defaults-to-zero
  (testing "tick-position with nil max-depth defaults to 0"
    (is (= 0 (scale/tick-position :tips nil 0)))
    (is (= 5 (scale/tick-position :root nil 5)))))

;; ===== label-value =====

(deftest label-value-tips-origin
  (testing "label-value with :tips origin subtracts tick from max-depth"
    (is (= 10 (scale/label-value :tips 10 0)))
    (is (= 7.5 (scale/label-value :tips 10 2.5)))
    (is (= 0 (scale/label-value :tips 10 10)))))

(deftest label-value-root-origin
  (testing "label-value with :root origin returns tick as-is"
    (is (= 0 (scale/label-value :root 10 0)))
    (is (= 2.5 (scale/label-value :root 10 2.5)))
    (is (= 10 (scale/label-value :root 10 10)))))

(deftest label-value-nil-origin-defaults-to-tips
  (testing "label-value with nil origin defaults to :tips"
    (is (= 10 (scale/label-value nil 10 0)))
    (is (= 0 (scale/label-value nil 10 10)))))

(deftest label-value-nil-max-depth-defaults-to-zero
  (testing "label-value with nil max-depth defaults to 0"
    (is (= 0 (scale/label-value :tips nil 0)))
    (is (= 5 (scale/label-value :root nil 5)))))

;; ===== tick-position and label-value invariants =====

(deftest tick-position-label-value-inverse-for-tips
  (testing "For :tips origin, tick-position and label-value are inverses"
    (let [max-depth 10
          tick 3]
      ;; label-value gives us the display value
      (let [display (scale/label-value :tips max-depth tick)]
        ;; tick-position should map that display value back to position
        (is (= tick (scale/tick-position :tips max-depth display)))))))

(deftest tick-position-label-value-identity-for-root
  (testing "For :root origin, tick-position and label-value are identity"
    (let [max-depth 10
          tick 3]
      (is (= tick (scale/tick-position :root max-depth tick)))
      (is (= tick (scale/label-value :root max-depth tick))))))

;; ===== scale-ticks =====

(deftest scale-ticks-zero-depth
  (testing "scale-ticks with zero max-depth returns single tick at 0"
    (let [result (scale/scale-ticks {:max-depth 0 :x-scale 100})]
      (is (= [0] (:major-ticks result)))
      (is (= [] (:minor-ticks result)))
      (is (= [0] (:base-ticks result)))
      (is (= 0 (:unit result))))))

(deftest scale-ticks-basic-root-origin
  (testing "scale-ticks generates ticks for :root origin"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 50
                                      :origin :root})]
      ;; Should have ticks
      (is (pos? (count (:major-ticks result))))
      (is (pos? (:unit result)))
      ;; All ticks should be >= 0 and <= max-depth
      (is (every? #(and (>= % 0) (<= % 10)) (:major-ticks result)))
      ;; Ticks should be sorted
      (is (= (:major-ticks result) (sort (:major-ticks result)))))))

(deftest scale-ticks-basic-tips-origin
  (testing "scale-ticks generates ticks for :tips origin"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 50
                                      :origin :tips})]
      ;; Should have ticks
      (is (pos? (count (:major-ticks result))))
      (is (pos? (:unit result)))
      ;; All ticks should be >= 0 and <= max-depth
      (is (every? #(and (>= % 0) (<= % 10)) (:major-ticks result)))
      ;; Ticks should be sorted
      (is (= (:major-ticks result) (sort (:major-ticks result)))))))

(deftest scale-ticks-default-origin-is-tips
  (testing "scale-ticks defaults to :tips origin"
    (let [result-default (scale/scale-ticks {:max-depth 10 :x-scale 50})
          result-tips (scale/scale-ticks {:max-depth 10 :x-scale 50 :origin :tips})]
      (is (= result-default result-tips)))))

(deftest scale-ticks-minor-ticks-count
  (testing "scale-ticks generates correct number of minor ticks between majors"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 500  ; Large scale to ensure multiple major ticks
                                      :minor-count 4
                                      :origin :root})]
      ;; With minor-count=4, we should have 4 minor ticks between each pair of major ticks
      ;; (if we have at least 2 major ticks)
      (when (>= (count (:major-ticks result)) 2)
        ;; Expected minors = (major_count - 1) * minor_count
        (let [expected-minor-count (* (dec (count (:major-ticks result))) 4)]
          (is (= expected-minor-count (count (:minor-ticks result)))))))))

(deftest scale-ticks-no-minor-ticks-when-count-zero
  (testing "scale-ticks generates no minor ticks when minor-count is 0"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 100
                                      :minor-count 0
                                      :origin :root})]
      (is (= [] (:minor-ticks result))))))

(deftest scale-ticks-base-ticks-complete
  (testing "base-ticks contains all ticks computed from unit interval"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 100
                                      :origin :root})]
      ;; base-ticks should be comprehensive set before filtering
      (is (pos? (count (:base-ticks result))))
      ;; major-ticks should be a subset of base-ticks
      (is (every? (set (:base-ticks result)) (:major-ticks result))))))

(deftest scale-ticks-all-ticks-sorted
  (testing "All tick arrays are sorted"
    (let [result (scale/scale-ticks {:max-depth 10
                                      :x-scale 100
                                      :origin :tips})]
      (is (= (:major-ticks result) (sort (:major-ticks result))))
      (is (= (:minor-ticks result) (sort (:minor-ticks result))))
      (is (= (:base-ticks result) (sort (:base-ticks result)))))))

(deftest scale-ticks-tips-vs-root-ordering
  (testing "Ordering of ticks are the same whether origin is :root or :tips"
    (let [max-depth 10
          result-root (scale/scale-ticks {:max-depth max-depth
                                          :x-scale 100
                                          :origin :root})
          result-tips (scale/scale-ticks {:max-depth max-depth
                                          :x-scale 100
                                          :origin :tips})]
      ;; For :tips, the highest position value corresponds to label 0
      ;; For :root, the lowest position value corresponds to label 0
      ;; Both should have same unit and count of ticks
      (is (= (:unit result-root) (:unit result-tips)))
      (is (= (count (:major-ticks result-root)) (count (:major-ticks result-tips))))
      ;; The first major tick for :root should be 0
      (is (= 0 (first (:major-ticks result-root))))
      ;; The last major tick for :root should be 10
      (is (= 10 (last (:major-ticks result-root))))
      ;; The first major tick for :tips should be 0
      (is (= 0 (first (:major-ticks result-tips))))
      ;; The last major tick for :tips should be 10
      (is (= 10 (last (:major-ticks result-tips)))))))

(deftest scale-ticks-unit-calculation
  (testing "scale-ticks unit is calculated via tree/calculate-scale-unit"
    (let [max-depth 10
          result (scale/scale-ticks {:max-depth max-depth :x-scale 100})]
      ;; Unit should match what calculate-scale-unit returns for max-depth/5
      (is (= (tree/calculate-scale-unit (/ max-depth 5)) (:unit result))))))

(deftest scale-ticks-min-label-px-affects-major-count
  (testing "Smaller min-label-px allows more major ticks"
    (let [result-narrow (scale/scale-ticks {:max-depth 10
                                            :x-scale 100
                                            :min-label-px 20
                                            :origin :root})
          result-wide (scale/scale-ticks {:max-depth 10
                                          :x-scale 100
                                          :min-label-px 100
                                          :origin :root})]
      ;; Narrower spacing should allow more or equal major ticks
      (is (>= (count (:major-ticks result-narrow))
              (count (:major-ticks result-wide)))))))

(deftest scale-ticks-small-values
  (testing "scale-ticks works with very small max-depth values"
    (let [result (scale/scale-ticks {:max-depth 0.05
                                      :x-scale 100
                                      :origin :root})]
      (is (pos? (count (:major-ticks result))))
      (is (pos? (:unit result)))
      (is (every? #(and (>= % 0) (<= % 0.05)) (:major-ticks result))))))

(deftest scale-ticks-large-values
  (testing "scale-ticks works with large max-depth values"
    (let [result (scale/scale-ticks {:max-depth 1000
                                      :x-scale 1
                                      :origin :root})]
      (is (pos? (count (:major-ticks result))))
      (is (pos? (:unit result)))
      (is (every? #(and (>= % 0) (<= % 1000)) (:major-ticks result))))))

;; ===== decimals-for-unit =====

(deftest decimals-for-unit-standard-units
  (testing "decimals-for-unit returns appropriate precision for typical units"
    ;; Unit 1.0 should give 1 decimal (log10(1) = 0, ceil = 0, max with 1 = 1)
    (is (= 1 (#'scale/decimals-for-unit 1.0)))
    ;; Unit 0.1 should give 1 decimal (log10(0.1) = -1, ceil(-(-1)) = 1)
    (is (= 1 (#'scale/decimals-for-unit 0.1)))
    ;; Unit 0.01 should give 2 decimals (log10(0.01) = -2, ceil(-(-2)) = 2)
    (is (= 2 (#'scale/decimals-for-unit 0.01)))
    ;; Unit 0.001 should give 3 decimals
    (is (= 3 (#'scale/decimals-for-unit 0.001)))
    ;; Unit 0.0001 should give 4 decimals (capped at 4)
    (is (= 4 (#'scale/decimals-for-unit 0.0001)))))

(deftest decimals-for-unit-very-small-units
  (testing "decimals-for-unit caps precision at 4 for very small units"
    ;; Even smaller units should be capped at 4
    (is (= 4 (#'scale/decimals-for-unit 0.00001)))
    (is (= 4 (#'scale/decimals-for-unit 0.000001)))
    (is (= 4 (#'scale/decimals-for-unit 1e-10)))))

(deftest decimals-for-unit-large-units
  (testing "decimals-for-unit returns minimum 1 decimal for large units"
    ;; Unit 10 should give 1 decimal (minimum)
    (is (= 1 (#'scale/decimals-for-unit 10)))
    ;; Unit 100 should give 1 decimal (minimum)
    (is (= 1 (#'scale/decimals-for-unit 100)))
    ;; Unit 1000 should give 1 decimal (minimum)
    (is (= 1 (#'scale/decimals-for-unit 1000)))))

(deftest decimals-for-unit-edge-cases
  (testing "decimals-for-unit handles edge cases"
    ;; Zero unit returns 1 (default)
    (is (= 1 (#'scale/decimals-for-unit 0)))
    ;; Negative unit returns 1 (default)
    (is (= 1 (#'scale/decimals-for-unit -1)))
    ;; nil returns 1 (default)
    (is (= 1 (#'scale/decimals-for-unit nil)))
    ;; Non-number returns 1 (default)
    (is (= 1 (#'scale/decimals-for-unit "not-a-number")))))

(deftest decimals-for-unit-fractional-units
  (testing "decimals-for-unit handles fractional units between powers of 10"
    ;; Unit 0.05 -> log10(0.05) ≈ -1.3, ceil(1.3) = 2
    (is (= 2 (#'scale/decimals-for-unit 0.05)))
    ;; Unit 0.5 -> log10(0.5) ≈ -0.3, ceil(0.3) = 1
    (is (= 1 (#'scale/decimals-for-unit 0.5)))
    ;; Unit 0.25 -> log10(0.25) ≈ -0.6, ceil(0.6) = 1
    (is (= 1 (#'scale/decimals-for-unit 0.25)))))

;; ===== label-decimals =====

(deftest label-decimals-typical-max-depth
  (testing "label-decimals returns appropriate precision for typical max-depth values"
    ;; max-depth 10 -> unit = calculate-scale-unit(2) = 0.5 -> decimals = 1
    (let [max-depth 10
          unit (tree/calculate-scale-unit (/ max-depth 5))]
      (is (= (#'scale/decimals-for-unit unit) (scale/label-decimals max-depth))))
    ;; max-depth 1 -> unit = calculate-scale-unit(0.2) = 0.1 -> decimals = 1
    (let [max-depth 1
          unit (tree/calculate-scale-unit (/ max-depth 5))]
      (is (= (#'scale/decimals-for-unit unit) (scale/label-decimals max-depth))))))

(deftest label-decimals-very-small-max-depth
  (testing "label-decimals handles very small max-depth values"
    ;; max-depth 0.01 -> unit = calculate-scale-unit(0.002) -> small unit -> more decimals
    (let [max-depth 0.01]
      (is (>= (scale/label-decimals max-depth) 1))
      (is (<= (scale/label-decimals max-depth) 4)))
    ;; max-depth 0.001
    (let [max-depth 0.001]
      (is (>= (scale/label-decimals max-depth) 1))
      (is (<= (scale/label-decimals max-depth) 4)))))

(deftest label-decimals-large-max-depth
  (testing "label-decimals handles large max-depth values"
    ;; max-depth 1000 -> unit = calculate-scale-unit(200) = 100 -> decimals = 1 (min)
    (is (= 1 (scale/label-decimals 1000)))
    ;; max-depth 10000
    (is (= 1 (scale/label-decimals 10000)))))

(deftest label-decimals-zero-max-depth
  (testing "label-decimals handles zero max-depth"
    ;; Zero max-depth uses unit = 1, which gives decimals = 1
    (is (= 1 (scale/label-decimals 0)))))

(deftest label-decimals-nil-max-depth
  (testing "label-decimals handles nil max-depth"
    ;; nil max-depth is treated as 0, which uses unit = 1
    (is (= 1 (scale/label-decimals nil)))))

(deftest label-decimals-negative-max-depth
  (testing "label-decimals handles negative max-depth"
    ;; Negative max-depth is treated as 0 (due to pos? check), uses unit = 1
    (is (= 1 (scale/label-decimals -10)))))

;; ===== format-label =====

(deftest format-label-basic-formatting
  (testing "format-label formats values with appropriate precision"
    ;; With max-depth 10, we expect 1 decimal place
    (is (= "5.0" (scale/format-label :root 10 5)))
    (is (= "2.5" (scale/format-label :root 10 2.5)))))

(deftest format-label-tips-origin
  (testing "format-label with :tips origin subtracts from max-depth"
    ;; max-depth 10, tick 3 -> value = 10 - 3 = 7
    (let [result (scale/format-label :tips 10 3)]
      ;; Should be "7.0" (with 1 decimal for max-depth 10)
      (is (= "7.0" result)))
    ;; max-depth 10, tick 10 -> value = 10 - 10 = 0
    (is (= "0.0" (scale/format-label :tips 10 10)))))

(deftest format-label-root-origin
  (testing "format-label with :root origin returns tick value"
    ;; max-depth 10, tick 3 -> value = 3
    (let [result (scale/format-label :root 10 3)]
      (is (= "3.0" result)))
    ;; max-depth 10, tick 0 -> value = 0
    (is (= "0.0" (scale/format-label :root 10 0)))))

(deftest format-label-small-max-depth
  (testing "format-label with small max-depth uses more decimal places"
    ;; max-depth 0.05 should require more decimals
    (let [result (scale/format-label :root 0.05 0.01)
          decimals (scale/label-decimals 0.05)]
      ;; Verify it has the expected number of decimals
      (is (>= decimals 2))
      ;; Result should contain a decimal point
      (is (re-find #"\." result)))))

(deftest format-label-large-max-depth
  (testing "format-label with large max-depth uses minimal decimal places"
    ;; max-depth 1000 should use 1 decimal
    (is (= "500.0" (scale/format-label :root 1000 500)))
    (is (= "1000.0" (scale/format-label :root 1000 1000)))))

(deftest format-label-zero-value
  (testing "format-label handles zero values correctly"
    (is (= "0.0" (scale/format-label :root 10 0)))
    (is (= "10.0" (scale/format-label :tips 10 0)))))

(deftest format-label-nil-max-depth
  (testing "format-label handles nil max-depth"
    ;; nil max-depth defaults to 0, which uses 1 decimal
    (is (= "5.0" (scale/format-label :root nil 5)))))

(deftest format-label-precision-matches-decimals
  (testing "format-label precision matches label-decimals output"
    (let [max-depth 10
          tick 3
          decimals (scale/label-decimals max-depth)
          formatted (scale/format-label :root max-depth tick)]
      ;; Count decimal places in formatted string
      (let [parts (clojure.string/split formatted #"\.")
            actual-decimals (if (> (count parts) 1)
                              (count (second parts))
                              0)]
        (is (= decimals actual-decimals))))))
