(ns app.csv
  (:require [clojure.string :as str]))

(defn parse-csv 
  "Parses a CSV/TSV string into a sequence of maps.
   Automatically detects comma or tab based on the first line."
  [content]
  (let [lines (-> content str/trim str/split-lines)
        first-line (first lines)
        ;; Simple auto-detection: if there's a tab, use tab, otherwise comma
        delimiter (if (str/includes? first-line "\t") #"\t" #",")
        headers (->> (str/split first-line delimiter)
                     (map #(-> % str/trim (str/replace #"^\"|\"$" "") keyword)))
        data-rows (rest lines)]
    (keep (fn [line]
            (when (not (str/blank? line))
              (let [values (map #(-> % str/trim (str/replace #"^\"|\"$" "")) 
                                (str/split line delimiter))]
                (zipmap headers values))))
          data-rows)))
