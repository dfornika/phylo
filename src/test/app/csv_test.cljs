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
