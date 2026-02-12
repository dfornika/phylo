(ns app.csv
  "Parses CSV and TSV text into ClojureScript data structures.

  Provides [[parse-csv]] for simple row-of-maps output and
  [[parse-metadata]] for richer output that includes column header
  configuration (labels, widths, detected types) suitable for UI rendering.

  Type detection classifies columns as `:date`, `:numeric`, or `:string`
  based on sampling all non-empty values."
  (:require [clojure.string :as str]))

;; ===== Date Parsing =====

(def ^:private iso-date-re
  "Matches YYYY-MM-DD format."
  #"^(\d{4})-(\d{1,2})-(\d{1,2})$")

(def ^:private dmy-date-re
  "Matches DD/MM/YYYY format."
  #"^(\d{1,2})/(\d{1,2})/(\d{4})$")

(defn parse-date
  "Attempts to parse a date string into a normalized `YYYY-MM-DD` string.

  Supports two formats:
  - ISO: `YYYY-MM-DD` (e.g. `\"2024-03-15\"`)
  - DMY: `DD/MM/YYYY` (e.g. `\"15/03/2024\"`)

  Returns the normalized `\"YYYY-MM-DD\"` string on success, or `nil`
  if the input is nil, empty, or does not match a known date format.

  Basic validation ensures month is 1–12 and day is 1–31, but does
  not check calendar correctness (e.g. Feb 30 would pass)."
  [s]
  (when (and s (not (str/blank? s)))
    (let [s (str/trim s)]
      (if-let [[_ y m d] (re-matches iso-date-re s)]
        (let [month (js/parseInt m 10)
              day   (js/parseInt d 10)]
          (when (and (<= 1 month 12) (<= 1 day 31))
            (str y "-"
                 (when (< month 10) "0") month "-"
                 (when (< day 10) "0") day)))
        (when-let [[_ d m y] (re-matches dmy-date-re s)]
          (let [month (js/parseInt m 10)
                day   (js/parseInt d 10)]
            (when (and (<= 1 month 12) (<= 1 day 31))
              (str y "-"
                   (when (< month 10) "0") month "-"
                   (when (< day 10) "0") day))))))))

;; ===== Column Type Detection =====

(defn- numeric?
  "Returns true if `s` is a non-empty string that parses as a finite number."
  [s]
  (and (not (str/blank? s))
       (let [n (js/Number s)]
         (and (not (js/isNaN n))
              (js/isFinite n)))))

(defn detect-column-type
  "Classifies a sequence of string values as `:date`, `:numeric`, or `:string`.

  Examines all non-empty values. If >=80% parse as dates, returns `:date`.
  Otherwise if >=80% parse as numbers, returns `:numeric`.
  Falls back to `:string`.

  Returns `:string` for empty input."
  [values]
  (let [non-empty (remove str/blank? values)
        total     (count non-empty)]
    (if (zero? total)
      :string
      (let [threshold    (* 0.8 total)
            date-count   (count (filter parse-date non-empty))
            num-count    (count (filter numeric? non-empty))]
        (cond
          (>= date-count threshold) :date
          (>= num-count threshold)  :numeric
          :else                     :string)))))

;; ===== CSV Parsing =====

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
                     (map #(-> % str/trim (str/replace #"^\"|\"$" "") keyword))
                     (into []))
        data-rows (into [] (rest lines))]
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

  Each column header is classified by type (`:date`, `:numeric`, or
  `:string`) via [[detect-column-type]], based on sampling all values
  in that column.

  Returns a map with keys:
  - `:headers` - vector of column config maps, each with:
    - `:key`   - keyword derived from the header string
    - `:label` - original header string for display
    - `:width` - column width in pixels
    - `:type`  - detected data type (`:date`, `:numeric`, or `:string`)
  - `:data`    - sequence of row maps keyed by header keywords"
  ([content]
   (parse-metadata content 120))
  ([content default-col-width]
   (let [lines (-> content str/trim str/split-lines)
         first-line (first lines)
         delimiter (if (str/includes? first-line "\t") #"\t" #",")
         raw-headers (map #(-> % str/trim (str/replace #"^\"|\"$" ""))
                          (str/split first-line delimiter))
         header-keys (mapv keyword raw-headers)
         data-rows (into [] (keep (fn [line]
                                    (when (not (str/blank? line))
                                      (let [values (map #(-> % str/trim (str/replace #"^\"|\"$" ""))
                                                        (str/split line delimiter))]
                                        (zipmap header-keys values))))
                                  (rest lines)))
         header-configs (mapv (fn [h k]
                                {:key   k
                                 :label h
                                 :width default-col-width
                                 :type  (detect-column-type (map #(get % k) data-rows))})
                              raw-headers header-keys)]
     {:headers header-configs
      :data data-rows})))
