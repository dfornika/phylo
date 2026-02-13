(ns app.generative-test
  "Property-based (generative) tests using test.check.

  These tests verify structural invariants across randomly generated
  inputs, complementing the example-based tests elsewhere.  Also
  includes `stest/check` tests that exercise fdef :args/:ret specs
  automatically."
  (:require [cljs.test :refer [deftest testing is]]
            [cljs.spec.alpha :as s]
            [cljs.spec.test.alpha :as stest]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [clojure.test.check.clojure-test :refer-macros [defspec]]
            [app.newick :as newick]
            [app.tree :as tree]
            [app.date :as date]
            [app.scale :as scale]
            [app.csv :as csv]
            [app.layout :as layout]
            [app.specs :as specs]
            [app.generators :as g]))

;; Number of trials per defspec
(def num-tests 50)

;; ===== Newick Round-Trip =====

(defspec newick-round-trip num-tests
  (prop/for-all [tree-map (g/gen-tree-map 3 3)]
    (let [newick-str (str (app.generators/tree-map->newick tree-map) ";")
          parsed    (newick/newick->map newick-str)
          re-serial (newick/map->newick parsed)]
      ;; Round-trip through newick->map and map->newick should be stable
      ;; (second round-trip equals first)
      (= re-serial (newick/map->newick (newick/newick->map re-serial))))))

;; ===== Tree Invariants =====

(defspec count-tips-equals-get-leaves num-tests
  (prop/for-all [tree-map (g/gen-tree-map 3 3)]
    (= (tree/count-tips tree-map)
       (count (tree/get-leaves
               (-> tree-map
                   (tree/assign-y-coords (atom 0))
                   first
                   tree/assign-x-coords
                   tree/assign-node-ids))))))

(defspec y-coords-strictly-increasing num-tests
  (prop/for-all [tree-map (g/gen-tree-map 3 3)]
    (let [positioned (-> tree-map
                         (tree/assign-y-coords (atom 0))
                         first
                         tree/assign-x-coords
                         tree/assign-node-ids)
          leaves (tree/get-leaves positioned)
          ys (map :y leaves)]
      ;; All leaf y-coordinates should be strictly increasing
      (every? true? (map < ys (rest ys))))))

(defspec x-coords-monotonic num-tests
  (prop/for-all [tree-map (g/gen-tree-map 3 3)]
    (let [positioned (-> tree-map
                         (tree/assign-y-coords (atom 0))
                         first
                         tree/assign-x-coords
                         tree/assign-node-ids)]
      ;; Every child's x should be >= parent's x
      (letfn [(check-monotonic [node]
                (every? (fn [child]
                          (and (>= (:x child) (:x node))
                               (check-monotonic child)))
                        (:children node)))]
        (check-monotonic positioned)))))

;; ===== parse-date Contract =====

(defspec parse-date-returns-string-or-nil num-tests
  (prop/for-all [date-str g/gen-date-str]
    (let [result (date/parse-date date-str)]
      ;; parse-date always returns nil or a string
      (or (nil? result) (string? result)))))

(deftest parse-date-valid-dates-return-strings
  (testing "Generated valid YYYY-MM-DD dates always parse successfully"
    (dotimes [_ 20]
      (let [year  (+ 1900 (rand-int 200))
            month (inc (rand-int 12))
            day   (inc (rand-int 28))
            s     (str year "-"
                       (.padStart (str month) 2 "0") "-"
                       (.padStart (str day) 2 "0"))
            result (date/parse-date s)]
        (is (string? result) (str "Expected string for valid date: " s))))))

;; ===== calculate-scale-unit Bounds =====

(defspec scale-unit-is-positive-and-bounded num-tests
  (prop/for-all [max-x g/gen-pos-number]
    (let [unit (scale/calculate-scale-unit max-x)]
      (and (pos? unit)
           (<= unit max-x)))))

;; ===== stest/check — fdef contract tests =====
;; These use the spec generators to verify :args/:ret contracts
;; automatically for pure functions.
;; In CLJS, stest/check is a macro requiring literal symbols.

(def ^:private check-opts
  {:clojure.spec.test.check/opts {:num-tests 20}})

(defn- check-passed?
  "Returns true when every check result in `results` passed."
  [results]
  (every? #(true? (get-in % [:clojure.spec.test.check/ret :pass?])) results))

(deftest stest-check-newick->map
  (testing "newick->map conforms to fdef contract"
    (let [results (stest/check `newick/newick->map check-opts)]
      (is (check-passed? results)
          (str "newick->map failed: " (pr-str results))))))

(deftest stest-check-map->newick
  (testing "map->newick conforms to fdef contract"
    (let [results (stest/check `newick/map->newick check-opts)]
      (is (check-passed? results)
          (str "map->newick failed: " (pr-str results))))))

(deftest stest-check-count-tips
  (testing "count-tips conforms to fdef contract"
    (let [results (stest/check `tree/count-tips check-opts)]
      (is (check-passed? results)
          (str "count-tips failed: " (pr-str results))))))

(deftest stest-check-parse-date
  (testing "parse-date conforms to fdef contract"
    (let [results (stest/check `date/parse-date check-opts)]
      (is (check-passed? results)
          (str "parse-date failed: " (pr-str results))))))

(deftest stest-check-parse-date-ms
  (testing "parse-date-ms conforms to fdef contract"
    (let [results (stest/check `date/parse-date-ms check-opts)]
      (is (check-passed? results)
          (str "parse-date-ms failed: " (pr-str results))))))

(deftest stest-check-calculate-scale-unit
  (testing "calculate-scale-unit conforms to fdef contract"
    (let [results (stest/check `scale/calculate-scale-unit check-opts)]
      (is (check-passed? results)
          (str "calculate-scale-unit failed: " (pr-str results))))))

(deftest stest-check-detect-column-type
  (testing "detect-column-type conforms to fdef contract"
    (let [results (stest/check `csv/detect-column-type check-opts)]
      (is (check-passed? results)
          (str "detect-column-type failed: " (pr-str results))))))

(deftest stest-check-compute-col-gaps
  (testing "compute-col-gaps conforms to fdef contract"
    (let [results (stest/check `layout/compute-col-gaps check-opts)]
      (is (check-passed? results)
          (str "compute-col-gaps failed: " (pr-str results))))))
