(ns app.export-html-test
  "Tests for standalone HTML export helpers in [[app.export.html]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.export.html :as export]))

(deftest inlineable-src-predicate
  (testing "Accepts normal paths and rejects data/blob/blank values"
    (is (true? (export/inlineable-src? "js/main.js")))
    (is (false? (export/inlineable-src? "data:text/plain,hello")))
    (is (false? (export/inlineable-src? "blob:abc123")))
    (is (false? (export/inlineable-src? "")))
    (is (false? (export/inlineable-src? nil)))))

(deftest escape-script-content-avoids-closing-tags
  (testing "Replaces closing script tags"
    (let [input "<script>ok</script>"
          output (export/escape-script-content input)]
      (is (not= input output))
      (is (re-find #"<\\/script>" output)))))

(deftest build-export-html-includes-embedded-content
  (testing "Builds a full HTML document with embedded assets"
    (let [html (export/build-export-html
                {:styles [{:url "css/style.css" :text "body{margin:0;}"}]
                 :scripts [{:url "js/main.js" :text "console.log('ok');"}]
                 :asset-map {"images/logo.svg" "data:image/svg+xml;base64,AAA"}
                 :state-edn "{:version 1 :state {}}"})]
      (is (re-find #"<!DOCTYPE html>" html))
      (is (re-find #"<div id=\"app\">" html))
      (is (re-find #"data-href=\"css/style.css\"" html))
      (is (re-find #"data-src=\"js/main.js\"" html))
      (is (re-find #"phylo-export-state" html))
      (is (re-find #"__PHYLO_ASSET_MAP__" html)))))
