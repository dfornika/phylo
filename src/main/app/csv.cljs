(ns app.csv
  "Parses CSV and TSV text into ClojureScript data structures.

  Provides [[parse-csv]] for simple row-of-maps output and
  [[parse-metadata]] for richer output that includes column header
  configuration (labels, widths, detected types) suitable for UI rendering.

  Type detection classifies columns as `:date`, `:numeric`, or `:string`
  based on sampling all non-empty values."
  (:require [clojure.string :as str]
            [app.date :as date]))


;; ===== CSV Serialization =====

(defn- csv-escape
  "Escapes a single CSV value with RFC4180-style quoting.

  Doubles any internal quotes and wraps the value in quotes only
  when it contains a comma, quote, or newline."
  [value]
  (let [s (str (or value ""))
        needs-quotes? (re-find #"[\",\n\r]" s)
        escaped (str/replace s "\"" "\"\"")]
    (if needs-quotes?
      (str "\"" escaped "\"")
      escaped)))

(defn metadata->csv
  "Serializes metadata rows into a CSV string.

  Uses the order of `active-cols` to emit a header row (via :label)
  and row values (via :key)."
  [active-cols rows]
  (if (seq active-cols)
    (let [keys (mapv :key active-cols)
          header (str/join "," (map csv-escape (map :label active-cols)))
          data-lines (map (fn [row]
                            (str/join "," (map (fn [k] (csv-escape (get row k ""))) keys)))
                          rows)]
      (str (str/join "\n" (cons header data-lines)) "\n"))
    ""))

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
            date-count   (count (filter date/parse-date non-empty))
            num-count    (count (filter numeric? non-empty))]
        (cond
          (>= date-count threshold) :date
          (>= num-count threshold)  :numeric
          :else                     :string)))))

;; ===== CSV Parsing =====

(defn- parse-delimited-line
  "Parses a single CSV/TSV line into fields, respecting RFC4180-style quotes."
  [line delimiter]
  (let [length (count line)]
    (loop [idx 0 in-quote? false field "" fields []]
      (if (>= idx length)
        (conj fields field)
        (let [ch (.charAt line idx)
              next-idx (inc idx)]
          (cond
            (= ch "\"")
            (if in-quote?
              (if (and (< next-idx length) (= (.charAt line next-idx) "\""))
                (recur (+ idx 2) true (str field "\"") fields)
                (recur next-idx false field fields))
              (recur next-idx true field fields))

            (and (not in-quote?) (= ch delimiter))
            (recur next-idx false "" (conj fields field))

            :else
            (recur next-idx in-quote? (str field ch) fields)))))))

(defn parse-csv
  "Parses a CSV/TSV string into a sequence of maps.

  Automatically detects the delimiter (tab or comma) by inspecting
  the first line. Handles RFC4180-style quoted values.
  Header strings are converted to keywords for map keys.

  Returns a sequence of maps, one per data row, keyed by the
  keyword-ified header names. Blank lines are skipped."
  [content]
  (let [lines (-> content str/trim str/split-lines)
        first-line (first lines)
        delimiter (if (str/includes? first-line "	") "	" ",")
        headers (->> (parse-delimited-line first-line (first delimiter))
                     (map #(-> % str/trim keyword))
                     (into []))
        data-rows (into [] (rest lines))]
    (keep (fn [line]
            (when (not (str/blank? line))
              (let [values (map str/trim
                                (parse-delimited-line line (first delimiter)))]
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
         delimiter (if (str/includes? first-line "	") "	" ",")
         raw-headers (map str/trim
                          (parse-delimited-line first-line (first delimiter)))
         header-keys (mapv keyword raw-headers)
         data-rows (into [] (keep (fn [line]
                                    (when (not (str/blank? line))
                                      (let [values (map str/trim
                                                        (parse-delimited-line line (first delimiter)))]
                                        (zipmap header-keys values))))
                                  (rest lines)))
         header-configs (mapv (fn [h k]
                                {:key   k
                                 :label h
                                 :width default-col-width
                                 :spacing 0
                                 :type  (detect-column-type (map #(get % k) data-rows))})
                              raw-headers header-keys)]
     {:headers header-configs
      :data data-rows})))
