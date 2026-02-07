(ns app.csv-test
  "Tests for CSV/TSV parsing in [[app.csv]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.csv :as csv]))

;; ===== parse-csv =====

(deftest parse-csv-comma-delimited
  (testing "Parses comma-delimited CSV into maps"
    (let [input "Name,Age,City\nAlice,30,Vancouver\nBob,25,Toronto"
          result (csv/parse-csv input)]
      (is (= 2 (count result)))
      (is (= "Alice" (:Name (first result))))
      (is (= "30" (:Age (first result))))
      (is (= "Toronto" (:City (second result)))))))

(deftest parse-csv-tab-delimited
  (testing "Parses tab-delimited TSV into maps"
    (let [input "ID\tLineage\tDate\nS001\tB.1.1.7\t2021-01-15\nS002\tP.1\t2021-02-20"
          result (csv/parse-csv input)]
      (is (= 2 (count result)))
      (is (= "S001" (:ID (first result))))
      (is (= "P.1" (:Lineage (second result)))))))

(deftest parse-csv-quoted-values
  (testing "Strips surrounding double quotes from values"
    (let [input "Name,Description\n\"Alice\",\"A person\""
          result (csv/parse-csv input)]
      (is (= "Alice" (:Name (first result))))
      (is (= "A person" (:Description (first result)))))))

(deftest parse-csv-skips-blank-lines
  (testing "Blank lines in input are skipped"
    (let [input "A,B\n1,2\n\n3,4\n"
          result (csv/parse-csv input)]
      (is (= 2 (count result))))))

;; ===== parse-metadata =====

(deftest parse-metadata-returns-headers-and-data
  (testing "Returns :headers and :data keys"
    (let [input "Sample,Lineage,Date\nS1,B.1,2021-01\nS2,P.1,2021-02"
          result (csv/parse-metadata input)]
      (is (contains? result :headers))
      (is (contains? result :data))
      (is (= 3 (count (:headers result))))
      (is (= 2 (count (:data result)))))))

(deftest parse-metadata-header-keys
  (testing "Each header has :key, :label, and :width"
    (let [input "Name,Value\nx,1"
          {:keys [headers]} (csv/parse-metadata input)]
      (is (every? #(contains? % :key) headers))
      (is (every? #(contains? % :label) headers))
      (is (every? #(contains? % :width) headers))
      (is (= :Name (:key (first headers))))
      (is (= "Name" (:label (first headers)))))))

(deftest parse-metadata-custom-col-width
  (testing "Custom column width is applied"
    (let [input "A,B\n1,2"
          {:keys [headers]} (csv/parse-metadata input 200)]
      (is (every? #(= 200 (:width %)) headers)))))

(deftest parse-metadata-default-col-width
  (testing "Default column width is 120"
    (let [input "A,B\n1,2"
          {:keys [headers]} (csv/parse-metadata input)]
      (is (every? #(= 120 (:width %)) headers)))))

(deftest parse-metadata-tab-detection
  (testing "Auto-detects tab delimiter in metadata"
    (let [input "ID\tGroup\nS1\tA\nS2\tB"
          {:keys [data]} (csv/parse-metadata input)]
      (is (= "A" (:Group (first data))))
      (is (= "B" (:Group (second data)))))))

;; ===== parse-date =====

(deftest parse-date-iso-format
  (testing "Parses YYYY-MM-DD format"
    (is (= "2024-03-15" (csv/parse-date "2024-03-15")))
    (is (= "2021-01-01" (csv/parse-date "2021-01-01")))))

(deftest parse-date-dd-mm-yyyy-format
  (testing "Parses DD/MM/YYYY format and normalizes to YYYY-MM-DD"
    (is (= "2024-03-15" (csv/parse-date "15/03/2024")))
    (is (= "2021-12-25" (csv/parse-date "25/12/2021")))))

(deftest parse-date-invalid-returns-nil
  (testing "Returns nil for non-date strings"
    (is (nil? (csv/parse-date "hello")))
    (is (nil? (csv/parse-date "123")))
    (is (nil? (csv/parse-date "")))
    (is (nil? (csv/parse-date nil)))))

(deftest parse-date-whitespace-trimmed
  (testing "Trims whitespace before parsing"
    (is (= "2024-01-01" (csv/parse-date "  2024-01-01  ")))
    (is (= "2024-01-01" (csv/parse-date " 01/01/2024 ")))))

;; ===== detect-column-type =====

(deftest detect-column-type-dates
  (testing "Detects date columns"
    (is (= :date (csv/detect-column-type ["2024-01-01" "2024-02-15" "2024-03-20"])))
    (is (= :date (csv/detect-column-type ["15/03/2024" "20/06/2024" "01/01/2023"])))))

(deftest detect-column-type-numeric
  (testing "Detects numeric columns"
    (is (= :numeric (csv/detect-column-type ["1.5" "2.3" "4.0"])))
    (is (= :numeric (csv/detect-column-type ["100" "200" "300"])))))

(deftest detect-column-type-string
  (testing "Detects string columns"
    (is (= :string (csv/detect-column-type ["alpha" "beta" "gamma"])))
    (is (= :string (csv/detect-column-type ["B.1.1.7" "P.1" "AY.4"])))))

(deftest detect-column-type-mixed-below-threshold
  (testing "Mixed types below 80% threshold fall through"
    ;; 2 out of 5 are dates = 40%, not enough
    (is (= :string (csv/detect-column-type ["2024-01-01" "2024-02-15" "foo" "bar" "baz"])))))

(deftest detect-column-type-empty
  (testing "Empty column returns :string"
    (is (= :string (csv/detect-column-type [])))))

;; ===== parse-metadata type detection =====

(deftest parse-metadata-detects-column-types
  (testing "Headers include :type based on data sniffing"
    (let [input "id,date,value,label\nS1,2024-01-01,1.5,alpha\nS2,2024-02-15,2.3,beta\nS3,2024-03-20,4.0,gamma"
          {:keys [headers]} (csv/parse-metadata input)]
      (is (= :string (:type (nth headers 0))))   ;; id column
      (is (= :date   (:type (nth headers 1))))   ;; date column
      (is (= :numeric (:type (nth headers 2))))  ;; value column
      (is (= :string (:type (nth headers 3)))))))  ;; label column
