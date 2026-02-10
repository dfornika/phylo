(ns app.arborview-import-test
  "Tests for ArborView HTML import parsing."
  (:require [cljs.test :refer [deftest testing is]]
            [app.import.arborview :as arbor]))

(deftest parse-arborview-html-basic
  (testing "Extracts TREE and DATA from ArborView HTML"
    (let [html "<script>const TREE = \"(A:1,B:2);\\n\"; const DATA = \"id\\tval\\nA\\t1\\n\";</script>"
          {:keys [newick-str metadata-raw]} (arbor/parse-arborview-html html)]
      (is (= "(A:1,B:2);\n" newick-str))
      (is (= "id\tval\nA\t1\n" metadata-raw)))))

(deftest parse-arborview-html-missing-data
  (testing "Handles missing DATA"
    (let [html "<script>let TREE = \"(X:0.1,Y:0.2);\";</script>"
          {:keys [newick-str metadata-raw]} (arbor/parse-arborview-html html)]
      (is (= "(X:0.1,Y:0.2);" newick-str))
      (is (nil? metadata-raw)))))

(deftest parse-arborview-html-single-quotes
  (testing "Extracts single-quoted JS strings"
    (let [html "<script>const TREE = '(A:1,B:2);'; const DATA = 'id\\tval\\nA\\t1\\n';</script>"
          {:keys [newick-str metadata-raw]} (arbor/parse-arborview-html html)]
      (is (= "(A:1,B:2);" newick-str))
      (is (= "id\tval\nA\t1\n" metadata-raw)))))

(deftest parse-arborview-html-unicode-escapes
  (testing "Unescapes \\uXXXX sequences"
    (let [html "<script>var TREE = \"(\\u0041:\\u0031);\";</script>"
          {:keys [newick-str]} (arbor/parse-arborview-html html)]
      (is (= "(A:1);" newick-str))))
  (testing "Unescapes \\xXX sequences"
    (let [html "<script>const DATA = \"\\x41\\x42\\x43\";</script>"
          {:keys [metadata-raw]} (arbor/parse-arborview-html html)]
      (is (= "ABC" metadata-raw)))))

(deftest parse-arborview-html-mixed-quotes
  (testing "Handles escaped quotes within strings"
    (let [html "<script>const TREE = \"(A\\\"1\\\":0.5);\";</script>"
          {:keys [newick-str]} (arbor/parse-arborview-html html)]
      (is (= "(A\"1\":0.5);" newick-str)))
    (let [html "<script>const TREE = '(A\\'1\\':0.5);';</script>"
          {:keys [newick-str]} (arbor/parse-arborview-html html)]
      (is (= "(A'1':0.5);" newick-str)))))
