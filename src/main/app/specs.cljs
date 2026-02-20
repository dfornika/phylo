(ns app.specs
  "Specs for the core data structures and function contracts in Phylo.

  Defines specs for Newick tree nodes, positioned tree nodes,
  metadata structures, UI component props, and the shared app state
  context. These specs serve as living documentation and can be used
  for validation and generative testing.

  Custom generators for recursive and domain-specific specs are
  registered separately in `app.spec-generators` (under `src/dev/`)
  so that `test.check` is not required on the production classpath.
  Load that namespace (via dev-preload or test requires) to enable
  `s/gen`, `s/exercise`, and `stest/check`.

  Sections:
  1. Utility functions (`validate-spec!`, `get-allowed-keys`)
  2. Tree data structures (`::tree-node`, `::positioned-node`)
  3. Bounding rectangle / scale ticks
  4. Metadata structures (`::metadata-header`, `::metadata-row`)
  5. Import result specs
  6. App state context (all atoms + setters)
  7. Component props
  8. Function specs (fdefs for newick, csv, date, tree, scale,
     layout, color, import modules)

  Dev integration:
  - `app.dev-preload` sets `expound/printer` and instruments all fdefs
  - `app.spec-generators` registers custom generators for key specs
  - `defui-with-spec` macro (in `app.specs` CLJ) injects dev-only
    prop validation into UIx components"
  (:require [cljs.spec.alpha :as s]
            [clojure.set]
            [camel-snake-kebab.core :as csk]))

(defn get-allowed-keys
  "Gets all allowed keys for a spec, including both required and optional."
  [spec]
  (when-let [form (s/form spec)]
    (let [{:keys [req-un opt-un]} (apply hash-map (rest form))]
      (set (concat (map #(keyword (name %)) req-un)
                   (map #(keyword (name %)) opt-un))))))

(comment
  (get-allowed-keys ::app-state))

(defn validate-spec!
  "Validates a value against a spec. Logs errors for invalid values
  and warnings for unexpected keys. Returns the value unchanged (for threading).
  
  Options:
  - check-unexpected-keys? (default false) - warn about keys not in spec"
  ([value spec label]
   (validate-spec! value spec label {}))
  ([value spec label {:keys [check-unexpected-keys?]
                      :or {check-unexpected-keys? false}}]

     ;; Check spec validity
   (when-not (s/valid? spec value)
     (js/console.error (str "Invalid " label ":")
                       (s/explain-str spec value)))

     ;; Check for unexpected keys (optional)
   (when (and check-unexpected-keys?
              (map? value))
     (when-let [allowed (get-allowed-keys spec)]
       (let [actual (set (keys value))
             unexpected (clojure.set/difference actual allowed)]
         (when (seq unexpected)
           (js/console.warn (str "Unexpected keys in " label ":")
                            (clj->js unexpected)
                            "\nAllowed:"
                            (clj->js allowed))))))
   value))

(defn atom-of [inner-spec]
  (s/and #(instance? Atom %)
         #(s/valid? inner-spec @%)))

;; ===== Primitive Specs =====

(s/def ::pos-number (s/and number? pos?))

(comment
  (let [test-props (clj->js {:helloThere "world"})]
    (js->clj test-props :key-fn csk/->kebab-case-keyword)))

;; ===== Tree Data Structures =====

(s/def ::name (s/nilable string?))
(s/def ::branch-length (s/nilable (s/and number? #(not (js/isNaN %)))))
(s/def ::children (s/coll-of ::tree-node :kind vector?))

;; For positioned nodes - children are ALSO positioned
(s/def ::positioned-children (s/coll-of ::positioned-node :kind vector?))

(s/def ::tree-node
  (s/keys :req-un [::name ::branch-length ::children]))

;; Positioned nodes have x/y coordinates assigned by layout algorithms
(s/def ::x (s/and number? #(not (js/isNaN %))))
(s/def ::y (s/and number? #(not (js/isNaN %))))
(s/def ::id nat-int?)

(s/def ::leaf-names (s/coll-of string? :kind set?))

(s/def ::positioned-node
  (s/keys :req-un [::name ::branch-length ::positioned-children ::x ::y ::id]
          :opt-un [::leaf-names]))

;; ===== Bounding Rectangle (for lasso selection) =====

(s/def ::min-x (s/and number? #(not (js/isNaN %))))
(s/def ::max-x (s/and number? #(not (js/isNaN %))))
(s/def ::min-y (s/and number? #(not (js/isNaN %))))
(s/def ::max-y (s/and number? #(not (js/isNaN %))))

;; ===== Scale Tick Output =====

(s/def ::major-ticks (s/coll-of number?))
(s/def ::minor-ticks (s/coll-of number?))

;; ===== Metadata Structures =====

(s/def ::key keyword?)
(s/def ::label string?)
(s/def ::width (s/and number? pos?))
(s/def ::spacing (s/and number? #(not (neg? %))))
(s/def ::column-type #{:date :numeric :string})

(s/def ::metadata-header
  (s/keys :req-un [::key ::label ::width]
          :opt-un [::column-type ::spacing]))

(s/def ::metadata-row
  (s/map-of keyword? string?))

(s/def ::headers (s/coll-of ::metadata-header))
(s/def ::data (s/coll-of ::metadata-row))

(s/def ::parsed-metadata
  (s/keys :req-un [::headers ::data]))

;; ===== Import Result Specs =====

(s/def ::error keyword?)
(s/def ::metadata-raw string?)

;; ===== App State Context =====

(s/def ::newick-str (s/nilable string?))
(s/def ::set-newick-str! fn?)

(s/def ::parsed-tree (s/nilable ::tree-node))
(s/def ::set-parsed-tree! fn?)

(s/def ::metadata-rows (s/coll-of ::metadata-row :kind vector?))
(s/def ::set-metadata-rows! fn?)

(s/def ::active-cols (s/coll-of ::metadata-header :kind vector?))
(s/def ::set-active-cols! fn?)

(s/def ::x-mult (s/and number? pos?))
(s/def ::set-x-mult! fn?)

(s/def ::y-mult (s/and number? pos?))
(s/def ::set-y-mult! fn?)

(s/def ::branch-length-mult (s/and number? pos?))
(s/def ::set-branch-length-mult! fn?)

(s/def ::scale-units-label string?)
(s/def ::set-scale-units-label! fn?)

(s/def ::show-internal-markers boolean?)
(s/def ::set-show-internal-markers! fn?)

(s/def ::show-scale-gridlines boolean?)
(s/def ::set-show-scale-gridlines! fn?)

(s/def ::show-distance-from-origin boolean?)
(s/def ::set-show-distance-from-origin! fn?)

(s/def ::show-distance-from-node boolean?)
(s/def ::set-show-distance-from-node! fn?)

(s/def ::scale-origin #{:tips :root})
(s/def ::set-scale-origin! fn?)

(s/def ::show-pixel-grid boolean?)
(s/def ::set-show-pixel-grid! fn?)

(s/def ::col-spacing (s/and number? #(not (neg? %))))
(s/def ::set-col-spacing! fn?)

(s/def ::left-shift-px (s/and number? #(not (js/isNaN %))))
(s/def ::set-left-shift-px! fn?)

(s/def ::tree-metadata-gap-px (s/and number? #(not (neg? %))))
(s/def ::set-tree-metadata-gap-px! fn?)

(s/def ::highlight-color string?)
(s/def ::set-highlight-color! fn?)

(s/def ::selected-ids (s/nilable (s/coll-of string? :kind set?)))
(s/def ::set-selected-ids! fn?)

(s/def ::active-reference-node-id (s/nilable nat-int?))
(s/def ::set-active-reference-node-id! fn?)
(s/def ::on-set-reroot-node (s/nilable fn?))

(s/def ::node-distances (s/nilable (s/map-of string? number?)))
(s/def ::reference-node-name (s/nilable string?))

(s/def ::highlights (s/nilable (s/map-of string? string?)))
(s/def ::set-highlights! fn?)

(s/def ::color-by-enabled? boolean?)
(s/def ::set-color-by-enabled! fn?)

(s/def ::color-by-field (s/nilable keyword?))
(s/def ::set-color-by-field! fn?)

(s/def ::color-by-palette #{:bright :contrast :pastel :blue-red :teal-gold})
(s/def ::set-color-by-palette! fn?)

(s/def ::color-by-type-override #{:auto :categorical :numeric :date})
(s/def ::set-color-by-type-override! fn?)

(s/def ::position (s/nilable (s/keys :req-un [::x ::y])))
(s/def ::set-position! fn?)

(s/def ::collapsed? boolean?)
(s/def ::set-collapsed! fn?)

(s/def ::legend-labels (s/nilable (s/map-of string? string?)))
(s/def ::set-legend-labels! fn?)

(s/def ::legend-visible? boolean?)
(s/def ::set-legend-visible! fn?)

(s/def ::metadata-panel-collapsed boolean?)
(s/def ::set-metadata-panel-collapsed! fn?)

(s/def ::metadata-panel-height (s/and number? #(not (neg? %))))
(s/def ::set-metadata-panel-height! fn?)

(s/def ::metadata-panel-last-drag-height (s/and number? #(not (neg? %))))
(s/def ::set-metadata-panel-last-drag-height! fn?)

;; ===== Component Props =====

(s/def ::columns (s/coll-of ::metadata-header))
(s/def ::start-offset (s/and number? #(not (js/isNaN %))))
(s/def ::column-label string?)
(s/def ::cell-height (s/and number? pos?))
(s/def ::tip-count nat-int?)
(s/def ::tree-height (s/and number? pos?))
(s/def ::sticky-header-width (s/and number? pos?))

(s/def ::tips (s/coll-of ::positioned-node))
(s/def ::x-offset (s/and number? #(not (js/isNaN %))))
(s/def ::y-scale (s/and number? pos?))
(s/def ::column-key keyword?)

(s/def ::col-width (s/and number? pos?))

(s/def ::parent-x (s/and number? #(not (js/isNaN %))))
(s/def ::parent-y (s/and number? #(not (js/isNaN %))))
(s/def ::line-color string?)
(s/def ::line-width (s/and number? pos?))

(s/def ::node ::positioned-node)
(s/def ::x-scale (s/and number? pos?))

(s/def ::marker-radius (s/and number? pos?))
(s/def ::marker-fill string?)
(s/def ::on-toggle-selection (s/nilable fn?))
(s/def ::on-select-subtree (s/nilable fn?))

;; Toolbar reads from context — no props spec needed.

(s/def ::tree ::positioned-node)
(s/def ::max-depth (s/and number? #(not (neg? %))))
(s/def ::width-px (s/and number? pos?))
(s/def ::component-height-px (s/nilable (s/and number? pos?)))
;; ::metadata-panel-collapsed, ::metadata-panel-height,
;; ::metadata-panel-last-drag-height, ::set-metadata-panel-height!,
;; ::set-metadata-panel-last-drag-height! defined in App State section above.

;; MetadataGrid — AG-Grid table with bidirectional selection sync.

(s/def ::on-cell-edited fn?)
(s/def ::on-cols-reordered fn?)
(s/def ::on-selection-changed fn?)

;; ResizablePanel — wrapper with draggable resize handle.

(s/def ::initial-height (s/and number? pos?))
(s/def ::min-height (s/and number? #(not (neg? %))))
(s/def ::max-height (s/and number? pos?))
(s/def ::height (s/and number? #(not (neg? %))))
(s/def ::on-height-change fn?)

;; ===== Function Specs =====

(s/fdef app.newick/newick->map
  :args (s/cat :s (s/nilable string?))
  :ret  (s/nilable ::tree-node))

(s/fdef app.csv/parse-csv
  :args (s/cat :content string?)
  :ret  (s/coll-of ::metadata-row))

(s/fdef app.csv/parse-metadata
  :args (s/cat :content string?
               :default-col-width (s/? number?))
  :ret  ::parsed-metadata)

(s/fdef app.date/parse-date
  :args (s/cat :s (s/nilable string?))
  :ret  (s/nilable string?))

(s/fdef app.csv/detect-column-type
  :args (s/cat :values (s/coll-of (s/nilable string?)))
  :ret  ::column-type)

(s/fdef app.tree/count-tips
  :args (s/cat :node ::tree-node)
  :ret  pos-int?)

(s/fdef app.tree/get-max-x
  :args (s/cat :node ::positioned-node)
  :ret  number?)

(s/fdef app.tree/get-leaves
  :args (s/cat :node ::positioned-node)
  :ret  (s/coll-of ::positioned-node))

(s/fdef app.tree/assign-x-coords
  :args (s/alt :node-only (s/cat :node ::tree-node)
               :positioned-node  (s/cat :node ::tree-node
                                        :current-x number?
                                        :is-root? boolean?))
  :ret  ::positioned-node)

(s/fdef app.tree/assign-leaf-names
  :args (s/cat :node ::tree-node)
  :ret  ::tree-node)

(s/fdef app.tree/leaves-in-rect
  :args (s/cat :tips (s/coll-of ::positioned-node)
               :rect (s/keys :req-un [::min-x ::max-x ::min-y ::max-y])
               :x-scale number?
               :y-mult number?
               :pad-x number?
               :pad-y number?
               :left-shift number?)
  :ret  (s/coll-of string? :kind set?))

(s/fdef app.scale/calculate-scale-unit
  :args (s/cat :max-x ::pos-number)
  :ret  ::pos-number)

(s/fdef app.scale/scale-ticks
  :args (s/cat :opts (s/keys :req-un [::max-depth ::x-scale]
                             :opt-un [::scale-origin]))
  :ret  (s/keys :req-un [::major-ticks ::minor-ticks]))

(s/fdef app.newick/map->newick
  :args (s/cat :node ::tree-node)
  :ret  string?)

(s/fdef app.layout/compute-col-gaps
  :args (s/cat :active-cols (s/coll-of ::metadata-header)
               :col-spacing (s/nilable number?))
  :ret  (s/coll-of number? :kind vector?))

(s/fdef app.util/clamp
  :args (s/cat :value number? :min-v number? :max-v number?)
  :ret  number?)

(s/fdef app.tree/assign-node-ids
  :args (s/alt :node-only (s/cat :node ::tree-node)
               :node-with-next-id (s/cat :node ::tree-node
                                         :next-id (atom-of nat-int?)))
  :ret  ::positioned-node)

(s/fdef app.tree/position-tree
  :args (s/cat :parsed-tree ::tree-node)
  :ret  (s/keys :req-un [::tree ::tips ::max-depth]))

(s/fdef app.tree/parse-and-position
  :args (s/cat :newick-str string?)
  :ret  (s/keys :req-un [::tree ::tips ::max-depth]))

(s/fdef app.tree/enrich-leaves
  :args (s/cat :tips (s/coll-of ::positioned-node)
               :metadata-rows (s/coll-of ::metadata-row)
               :active-cols (s/coll-of ::metadata-header))
  :ret  (s/coll-of ::positioned-node))

(s/fdef app.tree/prepare-tree
  :args (s/cat :newick-str string?
               :metadata-rows (s/coll-of ::metadata-row)
               :active-cols (s/coll-of ::metadata-header))
  :ret  (s/keys :req-un [::tree ::tips ::max-depth]))

(s/fdef app.tree/reroot-on-branch
  :args (s/cat :tree ::positioned-node :target-id nat-int?)
  :ret  (s/nilable ::tree-node))

(s/fdef app.tree/find-lca
  :args (s/cat :root ::positioned-node :id-a nat-int? :id-b nat-int?)
  :ret  (s/nilable ::positioned-node))

(s/fdef app.tree/distance-between
  :args (s/cat :root ::positioned-node :id-a nat-int? :id-b nat-int?)
  :ret  (s/nilable number?))

;; ----- Scale (additional) -----

(s/fdef app.scale/get-ticks
  :args (s/cat :max-x number? :unit number?)
  :ret  (s/coll-of number?))

(s/fdef app.scale/tick-position
  :args (s/cat :origin (s/nilable #{:tips :root})
               :max-depth (s/nilable number?)
               :label number?)
  :ret  number?)

(s/fdef app.scale/label-value
  :args (s/cat :origin (s/nilable #{:tips :root})
               :max-depth (s/nilable number?)
               :tick number?)
  :ret  number?)

(s/fdef app.scale/label-decimals
  :args (s/cat :max-depth (s/nilable number?))
  :ret  nat-int?)

(s/fdef app.scale/format-label
  :args (s/cat :origin (s/nilable #{:tips :root})
               :max-depth (s/nilable number?)
               :tick number?)
  :ret  string?)

;; ----- CSV (additional) -----

(s/fdef app.csv/metadata->csv
  :args (s/cat :active-cols (s/coll-of ::metadata-header)
               :rows (s/coll-of ::metadata-row))
  :ret  string?)

;; ----- Date (additional) -----

(s/fdef app.date/parse-date-ms
  :args (s/cat :s (s/nilable string?))
  :ret  (s/nilable number?))

;; ----- Import: Nextstrain -----

(s/fdef app.import.nextstrain/parse-nextstrain-json
  :args (s/cat :json-str (s/nilable string?))
  :ret  (s/nilable (s/or :success (s/keys :req-un [::newick-str ::parsed-tree])
                         :error   (s/keys :req-un [::error]))))

;; ----- Import: ArborView -----

(s/fdef app.import.arborview/parse-arborview-html
  :args (s/cat :html (s/nilable string?))
  :ret  (s/nilable (s/keys :opt-un [::newick-str ::metadata-raw])))

;; ----- Color -----

(s/def ::color-field-type #{:numeric :date :categorical})

(s/fdef app.color/infer-field-type
  :args (s/cat :metadata-rows (s/coll-of ::metadata-row)
               :field-key keyword?)
  :ret  ::color-field-type)

(s/fdef app.color/resolve-field-type
  :args (s/cat :values (s/coll-of (s/nilable string?))
               :type-override #{:auto :categorical :numeric :date})
  :ret  ::color-field-type)

(s/fdef app.color/build-color-map
  :args (s/cat :tips (s/coll-of map?)
               :field-key keyword?
               :palette-id (s/nilable keyword?)
               :type-override #{:auto :categorical :numeric :date})
  :ret  (s/map-of string? string?))

(s/fdef app.color/palette-options
  :args (s/cat :field-type ::color-field-type)
  :ret  (s/coll-of map?))
