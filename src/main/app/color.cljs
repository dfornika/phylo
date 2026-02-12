(ns app.color
  "Color scale helpers for metadata-driven tip coloring.

  Provides small built-in categorical palettes and gradient scales,
  along with utilities to infer field types and build color maps
  keyed by leaf name."
  (:require [clojure.string :as str]))

(def ^:private categorical-palettes
  {:bright {:label "Bright"
            :colors ["#e41a1c" "#377eb8" "#4daf4a" "#ff7f00"
                     "#984ea3" "#a65628" "#f781bf" "#999999"]}
   :contrast {:label "High Contrast"
              :colors ["#000000" "#d55e00" "#0072b2" "#009e73"
                       "#cc79a7" "#e69f00" "#56b4e9" "#f0e442"]}
   :pastel {:label "Pastel"
            :colors ["#fbb4ae" "#b3cde3" "#ccebc5" "#decbe4"
                     "#fed9a6" "#ffffcc" "#e5d8bd" "#fddaec"]}})

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
  [hex]
  (let [s (str/replace (str hex) "#" "")]
    (cond
      (= 3 (count s)) (apply str (mapcat (fn [ch] [ch ch]) s))
      (= 6 (count s)) s
      :else "000000")))

(defn- hex->rgb
  [hex]
  (let [s (normalize-hex hex)
        r (js/parseInt (subs s 0 2) 16)
        g (js/parseInt (subs s 2 4) 16)
        b (js/parseInt (subs s 4 6) 16)]
    [r g b]))

(defn- rgb->hex
  [r g b]
  (let [clamp (fn [n] (-> n js/Math.round (max 0) (min 255)))
        to-hex (fn [n]
                 (let [s (.toString (clamp n) 16)]
                   (if (= 1 (count s)) (str "0" s) s)))]
    (str "#" (to-hex r) (to-hex g) (to-hex b))))

(defn- lerp
  [a b t]
  (+ a (* (- b a) t)))

(defn- lerp-color
  [c1 c2 t]
  (let [[r1 g1 b1] (hex->rgb c1)
        [r2 g2 b2] (hex->rgb c2)]
    (rgb->hex (lerp r1 r2 t)
              (lerp g1 g2 t)
              (lerp b1 b2 t))))

(defn- gradient-color
  [colors t]
  (let [t (-> t (max 0) (min 1))]
    (cond
      (= 2 (count colors)) (lerp-color (first colors) (second colors) t)
      (>= 3 (count colors)) (if (<= t 0.5)
                              (lerp-color (first colors) (second colors) (/ t 0.5))
                              (lerp-color (second colors) (nth colors 2) (/ (- t 0.5) 0.5)))
      :else "#333333")))

(defn- non-empty-string?
  [value]
  (and (string? value) (not (str/blank? value))))

(defn- hierarchical-code?
  [value]
  (and (non-empty-string? value)
       (re-matches #"\d+(?:\.\d+)+" value)))

(defn- parse-number
  [value]
  (cond
    (number? value) value
    (non-empty-string? value)
    (let [n (js/parseFloat value)]
      (when-not (js/isNaN n) n))
    :else nil))

(defn- parse-date
  [value]
  (cond
    (number? value) value
    (non-empty-string? value)
    (let [t (js/Date.parse value)]
      (when-not (js/isNaN t) t))
    :else nil))

(defn infer-value-type
  "Infers field type from a collection of values.

  Returns {:type <keyword> :values <parsed>} where :values is numeric
  for numeric/date types or raw strings for categorical."
  [values]
  (let [values (->> values (filter non-empty-string?) vec)
        has-hierarchical? (some hierarchical-code? values)]
    (cond
      (empty? values) {:type :categorical :values []}
      has-hierarchical? {:type :categorical :values values}
      :else
      (let [nums (map parse-number values)
            all-num? (every? some? nums)
            dates (map parse-date values)
            all-date? (every? some? dates)]
        (cond
          all-num? {:type :numeric :values nums}
          all-date? {:type :date :values dates}
          :else {:type :categorical :values values})))))

(defn infer-field-type
  "Infers the field type from metadata rows for a given key."
  [metadata-rows field-key]
  (let [values (map #(get % field-key) metadata-rows)]
    (:type (infer-value-type values))))

(defn build-color-map
  "Builds a map of {leaf-name -> color} for the given field.

  Tips are expected to include :metadata maps keyed by field keywords.
  Palette id is optional and will fall back to a default for the field type."
  [tips field-key palette-id]
  (let [values (map #(get-in % [:metadata field-key]) tips)
        {:keys [type values]} (infer-value-type values)
        {:keys [palette]} (resolve-palette type palette-id)
        colors (:colors palette)]
    (cond
      (#{:numeric :date} type)
      (let [parsed (if (= type :date)
                     (keep parse-date values)
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
                            v (if (= type :date) (parse-date raw) (parse-number raw))
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
