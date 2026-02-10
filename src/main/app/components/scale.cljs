(ns app.components.scale
  "Shared scale tick calculation helpers for viewer and sticky header."
  (:require [app.tree :as tree]))

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
    (let [unit         (tree/calculate-scale-unit (/ max-depth 5))
          label-ticks  (tree/get-ticks max-depth unit)
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
