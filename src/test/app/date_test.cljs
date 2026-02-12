(ns app.date-test
  "Tests for date parsing in [[app.date]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.date :as date]))

;; ===== parse-date =====

(deftest parse-date-iso-format
  (testing "Parses YYYY-MM-DD format"
    (is (= "2024-03-15" (date/parse-date "2024-03-15")))
    (is (= "2021-01-01" (date/parse-date "2021-01-01")))))

(deftest parse-date-dd-mm-yyyy-format
  (testing "Parses DD/MM/YYYY format and normalizes to YYYY-MM-DD"
    (is (= "2024-03-15" (date/parse-date "15/03/2024")))
    (is (= "2021-12-25" (date/parse-date "25/12/2021")))))

(deftest parse-date-invalid-returns-nil
  (testing "Returns nil for non-date strings"
    (is (nil? (date/parse-date "hello")))
    (is (nil? (date/parse-date "123")))
    (is (nil? (date/parse-date "")))
    (is (nil? (date/parse-date nil)))))

(deftest parse-date-whitespace-trimmed
  (testing "Trims whitespace before parsing"
    (is (= "2024-01-01" (date/parse-date "  2024-01-01  ")))
    (is (= "2024-01-01" (date/parse-date " 01/01/2024 ")))))

;; ===== parse-date-ms =====

(deftest parse-date-ms-iso-format
  (testing "Parses YYYY-MM-DD to epoch ms"
    (is (number? (date/parse-date-ms "2024-03-15")))
    (is (number? (date/parse-date-ms "2021-01-01")))))

(deftest parse-date-ms-invalid-returns-nil
  (testing "Returns nil for invalid dates"
    (is (nil? (date/parse-date-ms "hello")))
    (is (nil? (date/parse-date-ms "123")))))

