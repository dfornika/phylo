(ns app.color
  "Color scale helpers for metadata-driven tip coloring.

  Provides small built-in categorical palettes and gradient scales,
  along with utilities to infer field types and build color maps
  keyed by leaf name."
  (:require [clojure.string :as str]
            [app.date :as date]))

(def ^:private categorical-palettes
  {:bright {:label "Bright"
            :colors ["#e41a1c"
                     "#377eb8"
                     "#4daf4a"
                     "#ff7f00"
                     "#984ea3"
                     "#a65628"
                     "#f781bf"
                     "#999999"]}

   :contrast {:label "High Contrast"
              :colors ["#000000"
                       "#d55e00"
                       "#0072b2"
                       "#009e73"
                       "#cc79a7"
                       "#e69f00"
                       "#56b4e9"
                       "#f0e442"]}

   :pastel {:label "Pastel"
            :colors ["#fbb4ae"
                     "#b3cde3"
                     "#ccebc5"
                     "#decbe4"
                     "#fed9a6"
                     "#ffffcc"
                     "#e5d8bd"
                     "#fddaec"]}})

(def ^:private gradient-palettes
  {:blue-red {:label "Blue-Red"
              :colors ["#2166ac" "#f7f7f7" "#b2182b"]}
   :teal-gold {:label "Teal-Gold"
               :colors ["#0b7285" "#f1f3f5" "#f59f00"]}})

(def ^:private categorical-order
  [:bright :contrast :pastel])

(def ^:private gradient-order
  [:blue-red :teal-gold])

(def ^:private default-categorical :bright)
(def ^:private default-gradient :blue-red)

(defn categorical-options
  "Returns ordered categorical palette option maps."
  []
  (mapv (fn [k] (assoc (get categorical-palettes k) :id k))
        categorical-order))

(defn gradient-options
  "Returns ordered gradient palette option maps."
  []
  (mapv (fn [k] (assoc (get gradient-palettes k) :id k))
        gradient-order))

(defn palette-options
  "Returns palette options appropriate for a field type.

  Numeric and date fields use gradient palettes; everything else uses
  categorical palettes."
  [field-type]
  (if (#{:numeric :date} field-type)
    (gradient-options)
    (categorical-options)))

(defn resolve-palette
  "Resolves a palette id for the given field type, falling back to a default.

  Returns {:id <keyword> :palette <map>}."
  [field-type palette-id]
  (let [gradient? (#{:numeric :date} field-type)
        palettes (if gradient? gradient-palettes categorical-palettes)
        default-id (if gradient? default-gradient default-categorical)
        id (if (contains? palettes palette-id) palette-id default-id)]
    {:id id
     :palette (get palettes id)}))

(defn- normalize-hex
  "Normalizes a hex color string to a 6-char lowercase hex without '#'."
  [hex]
  (let [s (str/replace (str hex) "#" "")]
    (cond
      (= 3 (count s)) (apply str (mapcat (fn [ch] [ch ch]) s))
      (= 6 (count s)) s
      :else "000000")))

(defn- hex->rgb
  "Converts a hex color string to an [r g b] vector."
  [hex]
  (let [s (normalize-hex hex)
        r (js/parseInt (subs s 0 2) 16)
        g (js/parseInt (subs s 2 4) 16)
        b (js/parseInt (subs s 4 6) 16)]
    [r g b]))

(defn- rgb->hex
  "Converts RGB components to a hex color string."
  [r g b]
  (let [clamp (fn [n] (-> n js/Math.round (max 0) (min 255)))
        to-hex (fn [n]
                 (let [s (.toString (clamp n) 16)]
                   (if (= 1 (count s)) (str "0" s) s)))]
    (str "#" (to-hex r) (to-hex g) (to-hex b))))

(defn- lerp
  "Linearly interpolates between a and b using t in [0,1]."
  [a b t]
  (+ a (* (- b a) t)))

(defn- lerp-color
  "Linearly interpolates between two hex colors at t in [0,1]."
  [c1 c2 t]
  (let [[r1 g1 b1] (hex->rgb c1)
        [r2 g2 b2] (hex->rgb c2)]
    (rgb->hex (lerp r1 r2 t)
              (lerp g1 g2 t)
              (lerp b1 b2 t))))

(defn- gradient-color
  "Returns a color from a 2-3 stop gradient for t in [0,1]."
  [colors t]
  (let [t (-> t (max 0) (min 1))]
    (cond
      (= 2 (count colors)) (lerp-color (first colors) (second colors) t)
      (>= 3 (count colors)) (if (<= t 0.5)
                              (lerp-color (first colors) (second colors) (/ t 0.5))
                              (lerp-color (second colors) (nth colors 2) (/ (- t 0.5) 0.5)))
      :else "#333333")))

(def ^:private legend-bin-count
  "Default number of bins for numeric/date legend entries."
  5)

(defn- non-empty-string?
  "True when value is a non-blank string."
  [value]
  (and (string? value) (not (str/blank? value))))

(defn- trim-trailing-zeros
  "Trims trailing fractional zeros from a numeric string."
  [s]
  (cond
    (str/blank? s) ""
    (str/includes? s ".") (-> s
                              (str/replace #"(\.\d*?)0+$" "$1")
                              (str/replace #"\.$" ""))
    :else s))

(defn- truncate-to
  "Truncates a number to a fixed number of decimal places."
  [n decimals]
  (let [factor (js/Math.pow 10 decimals)]
    (/ (js/Math.trunc (* n factor)) factor)))

(defn- format-number
  "Formats a number to a compact string with adaptive precision.

  Uses truncation (not rounding) to avoid expanding upper bounds in ranges."
  [n]
  (let [abs (js/Math.abs n)
        decimals (cond
                   (>= abs 1000) 0
                   (>= abs 100) 1
                   (>= abs 1) 2
                   :else 3)
        truncated (truncate-to n decimals)
        s (.toFixed truncated decimals)]
    (trim-trailing-zeros s)))

(defn- format-date-ms
  "Formats epoch milliseconds as YYYY-MM-DD in UTC."
  [ms]
  (let [date (js/Date. ms)
        year (.getUTCFullYear date)
        month (-> (.getUTCMonth date) inc (str) (.padStart 2 "0"))
        day (-> (.getUTCDate date) (str) (.padStart 2 "0"))]
    (str year "-" month "-" day)))

(defn- parse-number
  "Parses a numeric value from a string or returns the number."
  [value]
  (cond
    (nil? value) nil
    (js/isNaN value) nil
    (number? value) value
    (parse-long value) (parse-long value)
    (parse-double value) (parse-double value)
    :else nil))

(def ^:private parse-success-threshold
  "Minimum fraction of non-empty values that must parse successfully
  as a given type (number/date) for that type to be inferred."
  0.9)

(defn- parse-success-ratio
  "Given a sequence of parsed values (some may be nil), returns the
  fraction that are non-nil. Returns 0 for an empty sequence."
  [parsed-values]
  (let [total   (count parsed-values)
        success (count (keep identity parsed-values))]
    (if (pos? total)
      (/ success total)
      0)))

(defn infer-value-type
  "Infers field type from a collection of values.

  Returns {:type <keyword> :values <parsed>} where :values is numeric
  for numeric/date types (with non-parsing values ignored) or raw
  strings for categorical."
  [values]
  (let [values          (->> values (filter non-empty-string?) vec)
        nums            (map parse-number values)
        num-success     (vec (keep identity nums))
        num-ratio       (parse-success-ratio nums)
        dates           (map date/parse-date-ms values)
        date-success    (vec (keep identity dates))
        date-ratio      (parse-success-ratio dates)
        numeric-enough? (and (pos? (count num-success))
                             (>= num-ratio parse-success-threshold))
        date-enough?    (and (pos? (count date-success))
                             (>= date-ratio parse-success-threshold))]
    (cond
      (not (seq values)) {:type :categorical :values []}
      numeric-enough? {:type :numeric :values num-success}
      date-enough?    {:type :date :values date-success}
      :else           {:type :categorical :values values})))

(defn infer-field-type
  "Infers the field type from metadata rows for a given key."
  [metadata-rows field-key]
  (let [values (map #(get % field-key) metadata-rows)]
    (:type (infer-value-type values))))

(defn resolve-field-type
  "Resolves the effective field type, honoring an override when present."
  [values type-override]
  (let [override (if (#{:auto :categorical :numeric :date} type-override)
                   type-override
                   :auto)
        inferred (:type (infer-value-type values))]
    (if (= override :auto) inferred override)))

(defn build-color-map
  "Builds a map of {leaf-name -> color} for the given field.

  Tips are expected to include :metadata maps keyed by field keywords.
  Palette id is optional and will fall back to a default for the field type."
  [tips field-key palette-id type-override]
  (let [values (map #(get-in % [:metadata field-key]) tips)
        type (resolve-field-type values type-override)
        {:keys [palette]} (resolve-palette type palette-id)
        colors (:colors palette)]
    (cond
      (#{:numeric :date} type)
      (let [parsed (if (= type :date)
                     (keep date/parse-date-ms values)
                     (keep parse-number values))
            min-v (when (seq parsed) (apply min parsed))
            max-v (when (seq parsed) (apply max parsed))
            span (when (and min-v max-v) (- max-v min-v))
            to-color (fn [v]
                       (when v
                         (let [t (if (and span (pos? span)) (/ (- v min-v) span) 0.5)]
                           (gradient-color colors t))))]
        (into {}
              (keep (fn [tip]
                      (let [raw (get-in tip [:metadata field-key])
                            v (if (= type :date) (date/parse-date-ms raw) (parse-number raw))
                            color (to-color v)]
                        (when color
                          [(:name tip) color]))))
              tips))

      :else
      (let [unique-values (sort (set (filter non-empty-string? values)))
            color-cycle (cycle colors)
            value->color (zipmap unique-values color-cycle)]
        (into {}
              (keep (fn [tip]
                      (let [raw (get-in tip [:metadata field-key])
                            color (get value->color raw)]
                        (when color
                          [(:name tip) color]))))
              tips)))))

(defn- format-range
  "Formats a legend range label for numeric or date values."
  [field-type start end]
  (let [fmt (if (= field-type :date) format-date-ms format-number)
        start-label (fmt start)
        end-label (fmt end)]
    (if (= start-label end-label)
      start-label
      (str start-label "-" end-label))))

(defn build-legend
  "Builds legend entries for the given field.

  Returns {:type <keyword> :entries <vector>} with entries of
  {:id <string> :label <string> :color <hex>}.
  Numeric/date fields are binned into ranges; categorical fields
  list each unique value."
  [tips field-key palette-id type-override]
  (let [values (map #(get-in % [:metadata field-key]) tips)
        field-type (resolve-field-type values type-override)
        {:keys [palette]} (resolve-palette field-type palette-id)
        colors (:colors palette)]
    (cond
      (#{:numeric :date} field-type)
      (let [parsed (if (= field-type :date)
                     (keep date/parse-date-ms values)
                     (keep parse-number values))
            min-v (when (seq parsed) (apply min parsed))
            max-v (when (seq parsed) (apply max parsed))
            span (when (and min-v max-v) (- max-v min-v))]
        (if (and min-v max-v (pos? (or span 0)))
          (let [step (/ span legend-bin-count)
                entries (mapv (fn [idx]
                                (let [start (+ min-v (* idx step))
                                      end (if (= idx (dec legend-bin-count))
                                            max-v
                                            (+ min-v (* (inc idx) step)))
                                      t (/ (+ idx 0.5) legend-bin-count)]
                                  {:id (str "bin-" idx)
                                   :label (format-range field-type start end)
                                   :color (gradient-color colors t)}))
                              (range legend-bin-count))]
            {:type field-type :entries entries})
          (let [fallback-color (gradient-color colors 0.5)
                label (when min-v (format-range field-type min-v max-v))]
            {:type field-type
             :entries (if label
                        [{:id "single" :label label :color fallback-color}]
                        [])})))

      :else
      (let [unique-values (sort (set (filter non-empty-string? values)))
            value->color (zipmap unique-values (cycle colors))
            entries (mapv (fn [value]
                            {:id (str value)
                             :label value
                             :color (get value->color value)})
                          unique-values)]
        {:type :categorical :entries entries}))))

;; ===== Legend Section Assembly =====

(defn build-legend-sections
  "Assembles legend sections for display from auto-coloring and custom highlights.

  Takes the legend built by `build-legend`, along with custom highlight data,
  and returns a vector of `{:title string :entries [...]}` section maps.

  Arguments:
  - `auto-legend`    - result of `build-legend` (may be nil)
  - `field-label`    - display label for the auto-colored field (e.g. \"Lineage\")
  - `highlights`     - map of `{leaf-name -> color}` for custom highlights
  - `legend-labels`  - map of `{color -> user-label}` for custom legend entries

  Returns `{:sections [...] :show? bool}` where `:show?` is true when
  any legend sections exist."
  [auto-legend field-label highlights legend-labels]
  (let [auto-entries (or (:entries auto-legend) [])
        custom-colors (->> (vals (or highlights {})) distinct sort)
        custom-entries (->> custom-colors
                            (mapv (fn [color]
                                    (let [label (get legend-labels color "")
                                          placeholder? (str/blank? label)
                                          display (if placeholder? "Label..." label)]
                                      {:id (str "custom-" color)
                                       :color color
                                       :label display
                                       :editable? true
                                       :placeholder? placeholder?}))))
        sections (cond-> []
                   (seq auto-entries)
                   (conj {:title (if field-label
                                   (str "Auto: " field-label)
                                   "Auto")
                          :entries auto-entries})
                   (seq custom-entries)
                   (conj {:title "Custom" :entries custom-entries}))]
    {:sections sections
     :show? (boolean (seq sections))}))
