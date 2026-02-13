(ns app.scale
  "Scale bar calculation helpers for the phylogenetic tree viewer.

  Contains pure functions for computing tick positions, formatting
  labels, and calculating human-friendly scale intervals. Used by
  viewer, tree, and metadata components.")

;; ===== Scale Unit Calculation =====

(defn calculate-scale-unit
  "Calculates a human-friendly tick interval for a scale bar.

  Given a maximum value, returns a 'nice' unit size based on the
  order of magnitude. The algorithm picks the largest round number
  that produces a reasonable number of ticks:
  - ratio < 2 -> 10% of magnitude
  - ratio < 5 -> 50% of magnitude
  - otherwise  -> full magnitude

  For example, `(calculate-scale-unit 0.37)` returns `0.05`."
  [max-x]
  (let [log10 (js/Math.log10 max-x)
        magnitude (js/Math.pow 10 (js/Math.floor log10))
        ratio (/ max-x magnitude)]
    (cond
      (< ratio 2) (* magnitude 0.1)
      (< ratio 5) (* magnitude 0.5)
      :else magnitude)))

(defn get-ticks
  "Generates a lazy sequence of tick positions from 0 to `max-x` in
  increments of `unit`. Used to render scale bar gridlines and labels.

  Guards against non-positive `unit` to avoid a non-terminating sequence:
  - If `max-x` is <= 0, returns a single tick at 0.
  - If `unit` is <= 0 (and `max-x` is > 0), returns an empty sequence."
  [max-x unit]
  (cond
    (<= max-x 0) [0]
    (<= unit 0)  []
    :else        (take-while #(<= % max-x)
                             (iterate #(+ % unit) 0))))

;; ===== Tick Position & Label Helpers =====

(defn- every-nth
  "Returns every Nth element from `xs`, starting at index 0."
  [xs n]
  (keep-indexed (fn [idx v]
                  (when (zero? (mod idx n)) v))
                xs))

(defn tick-position
  "Returns the x-position for a label tick based on scale origin."
  [origin max-depth label]
  (let [depth (or max-depth 0)
        origin (or origin :tips)]
    (if (= origin :tips)
      (- depth label)
      label)))

(defn label-value
  "Returns the display value for a tick based on scale origin."
  [origin max-depth tick]
  (let [depth (or max-depth 0)
        origin (or origin :tips)]
    (if (= origin :tips)
      (- depth tick)
      tick)))

(defn- decimals-for-unit
  "Returns a display precision based on the unit size."
  [unit]
  (if (and (number? unit) (pos? unit))
    (-> (- (js/Math.log10 unit))
        (js/Math.ceil)
        (int)
        (max 1)
        (min 4))
    1))

(defn label-decimals
  "Returns the number of decimals to show for scale labels."
  [max-depth]
  (let [depth (or max-depth 0)
        unit (if (pos? depth)
               (calculate-scale-unit (/ depth 5))
               1)]
    (decimals-for-unit unit)))

(defn format-label
  "Formats a scale label with precision based on max-depth."
  [origin max-depth tick]
  (let [value (label-value origin max-depth tick)
        decimals (label-decimals max-depth)]
    (.toFixed (js/Number value) decimals)))

(defn scale-ticks
  "Computes major and minor ticks for scale bars.

  Options:
  - `:max-depth`    - maximum x-coordinate in the tree
  - `:x-scale`      - horizontal scaling factor (pixels per branch-length unit)
  - `:min-label-px` - minimum pixel spacing between labeled ticks (default 48)
  - `:minor-count`  - number of minor ticks between labeled ticks (default 4)
  - `:origin`       - `:tips` or `:root` for tick placement

  Returns {:major-ticks [...] :minor-ticks [...] :base-ticks [...] :unit n}.
  "
  [{:keys [max-depth x-scale min-label-px minor-count origin]
    :or {min-label-px 48
         minor-count 4
         origin :tips}}]
  (if (pos? max-depth)
    (let [unit         (calculate-scale-unit (/ max-depth 5))
          label-ticks  (get-ticks max-depth unit)
          width-px     (* max-depth (max 0 (or x-scale 0)))
          max-labels   (max 1 (int (js/Math.floor (/ width-px min-label-px))))
          every-n      (max 1 (int (js/Math.ceil (/ (count label-ticks) max-labels))))
          major-labels (vec (every-nth label-ticks every-n))
          minor-labels (if (and (seq major-labels) (pos? minor-count))
                         (vec
                          (mapcat (fn [[a b]]
                                    (let [step (/ (- b a) (inc minor-count))]
                                      (map #(+ a (* step %))
                                           (range 1 (inc minor-count)))))
                                  (partition 2 1 major-labels)))
                         [])
          base-ticks   (->> label-ticks
                            (map #(tick-position origin max-depth %))
                            sort
                            vec)
          major-ticks  (->> major-labels
                            (map #(tick-position origin max-depth %))
                            sort
                            vec)
          minor-ticks  (->> minor-labels
                            (map #(tick-position origin max-depth %))
                            sort
                            vec)]
      {:major-ticks major-ticks
       :minor-ticks minor-ticks
       :base-ticks base-ticks
       :unit unit})
    {:major-ticks [0]
     :minor-ticks []
     :base-ticks [0]
     :unit 0}))
