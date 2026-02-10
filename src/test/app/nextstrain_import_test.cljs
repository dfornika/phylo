(ns app.nextstrain-import-test
  "Tests for Nextstrain JSON import parsing."
  (:require [cljs.test :refer [deftest testing is]]
            [app.import.nextstrain :as nextstrain]))

(deftest parse-nextstrain-json-basic
  (testing "Converts a simple Nextstrain tree into Newick"
    (let [json "{\"tree\":{\"name\":\"root\",\"node_attrs\":{\"div\":0},\"children\":[{\"name\":\"A\",\"node_attrs\":{\"div\":0.1}},{\"name\":\"B\",\"node_attrs\":{\"div\":{\"value\":0.2}}}]}}"
          {:keys [newick-str]} (nextstrain/parse-nextstrain-json json)]
      (is (= "(A:0.1,B:0.2)root;" newick-str)))))

(deftest parse-nextstrain-json-missing-div
  (testing "Defaults missing div to zero branch length"
    (let [json "{\"tree\":{\"name\":\"root\",\"node_attrs\":{\"div\":0},\"children\":[{\"name\":\"A\"}]}}"
          {:keys [newick-str]} (nextstrain/parse-nextstrain-json json)]
      (is (= "(A:0)root;" newick-str)))))

(deftest parse-nextstrain-json-escape-name
  (testing "Escapes names with spaces for Newick"
    (let [json "{\"tree\":{\"name\":\"root\",\"node_attrs\":{\"div\":0},\"children\":[{\"name\":\"A B\",\"node_attrs\":{\"div\":0.1}}]}}"
          {:keys [newick-str]} (nextstrain/parse-nextstrain-json json)]
      (is (= "('A B':0.1)root;" newick-str)))))
