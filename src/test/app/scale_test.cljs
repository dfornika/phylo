(ns app.scale-test
  "Tests for scale tick calculation functions in [[app.components.scale]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.components.scale :as scale]
            [app.tree :as tree]))

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
