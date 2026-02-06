(ns app.csv
  "Parses CSV and TSV text into ClojureScript data structures.

  Provides [[parse-csv]] for simple row-of-maps output and
  [[parse-metadata]] for richer output that includes column header
  configuration (labels, widths) suitable for UI rendering."
  (:require [clojure.string :as str]))

(defn parse-csv
  "Parses a CSV/TSV string into a sequence of maps.

  Automatically detects the delimiter (tab or comma) by inspecting
  the first line. Strips surrounding double quotes from values.
  Header strings are converted to keywords for map keys.

  Returns a sequence of maps, one per data row, keyed by the
  keyword-ified header names. Blank lines are skipped."
  [content]
  (let [lines (-> content str/trim str/split-lines)
        first-line (first lines)
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

(defn parse-metadata
  "Parses a CSV/TSV string into a map with column metadata and data rows.

  Like [[parse-csv]], but returns a richer structure suitable for
  rendering metadata columns alongside a phylogenetic tree. The
  delimiter is auto-detected from the first line.

  `default-col-width` is the pixel width assigned to each column
  (defaults to 120 if not provided).

  Returns a map with keys:
  - `:headers` - vector of column config maps, each with:
    - `:key`   - keyword derived from the header string
    - `:label` - original header string for display
    - `:width` - column width in pixels
  - `:data`    - sequence of row maps keyed by header keywords"
  ([content]
   (parse-metadata content 120))
  ([content default-col-width]
   (let [lines (-> content str/trim str/split-lines)
         first-line (first lines)
         delimiter (if (str/includes? first-line "\t") #"\t" #",")
         raw-headers (map #(-> % str/trim (str/replace #"^\"|\"$" ""))
                          (str/split first-line delimiter))
         header-configs (mapv (fn [h]
                                {:key (keyword h)
                                 :label h
                                 :width default-col-width})
                              raw-headers)
         data-rows (keep (fn [line]
                           (when (not (str/blank? line))
                             (let [values (map #(-> % str/trim (str/replace #"^\"|\"$" ""))
                                               (str/split line delimiter))]
                               (zipmap (map :key header-configs) values))))
                         (rest lines))]
     {:headers header-configs
      :data data-rows})))
