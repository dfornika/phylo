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
