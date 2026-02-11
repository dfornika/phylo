(ns app.specs
  "Specs for the core data structures and function contracts in Phylo.

  Defines specs for Newick tree nodes, positioned tree nodes,
  metadata structures, UI component props, and the shared app state
  context. These specs serve as living documentation and can be used
  for validation and generative testing.

  Require this namespace in the REPL or in dev preloads to
  enable `cljs.spec.test.alpha/instrument` on key functions."
  (:require [cljs.spec.alpha :as s]
            [clojure.set :as set]))


(defn get-allowed-keys
  "Gets all allowed keys for a spec, including both required and optional."
  [spec]
  (when-let [form (s/form spec)]
    (let [{:keys [req-un opt-un]} (apply hash-map (rest form))]
      (set (concat (map #(keyword (name %)) req-un)
                   (map #(keyword (name %)) opt-un))))))

(comment
  (get-allowed-keys ::app-state)
)

(defn validate-spec!
  "Validates a value against a spec in dev mode. Logs errors for invalid values
  and warnings for unexpected keys. Returns the value unchanged (for threading).
  
  Options:
  - check-unexpected-keys? (default true) - warn about keys not in spec"
  ([value spec label]
   (validate-spec! value spec label {}))
  ([value spec label {:keys [check-unexpected-keys?]
                      :or {check-unexpected-keys? true}}]
   (when ^boolean goog.DEBUG
     ;; Check spec validity
     (when-not (s/valid? spec value)
       (js/console.error (str "Invalid " label ":")
                         (s/explain-str spec value)))
     
     ;; Check for unexpected keys (optional)
     (when (and check-unexpected-keys?
                (map? value))
       (when-let [allowed (get-allowed-keys spec)]
         (let [actual (set (keys value))
               unexpected (set/difference actual allowed)]
           (when (seq unexpected)
             (js/console.warn (str "Unexpected keys in " label ":") 
                             (clj->js unexpected)
                             "\nAllowed:" 
                             (clj->js allowed)))))))
   value))

(defn with-spec-check
  "Wraps a component function to validate its props against a spec in dev mode."
  [component spec]
  (fn [props]
    (validate-spec! props spec "component props")
    (component props)))


;; ===== Tree Data Structures =====

(s/def ::name (s/nilable string?))
(s/def ::branch-length (s/nilable number?))
(s/def ::children (s/coll-of ::tree-node :kind vector?))

(s/def ::tree-node
  (s/keys :req-un [::name ::branch-length ::children]))

;; Positioned nodes have x/y coordinates assigned by layout algorithms
(s/def ::x number?)
(s/def ::y number?)
(s/def ::id nat-int?)

(s/def ::positioned-node
  (s/keys :req-un [::name ::branch-length ::children ::x ::y ::id]))

;; ===== Metadata Structures =====

(s/def ::key keyword?)
(s/def ::label string?)
(s/def ::width number?)
(s/def ::column-type #{:date :numeric :string})

(s/def ::metadata-header
  (s/keys :req-un [::key ::label ::width]
          :opt-un [::column-type]))

(s/def ::metadata-row
  (s/map-of keyword? string?))

(s/def ::headers (s/coll-of ::metadata-header))
(s/def ::data (s/coll-of ::metadata-row))

(s/def ::parsed-metadata
  (s/keys :req-un [::headers ::data]))

;; ===== App State Context =====

(s/def ::newick-str (s/nilable string?))
(s/def ::set-newick-str! fn?)

(s/def ::metadata-rows (s/coll-of ::metadata-row :kind vector?))
(s/def ::set-metadata-rows! fn?)

(s/def ::active-cols (s/coll-of ::metadata-header :kind vector?))
(s/def ::set-active-cols! fn?)

(s/def ::x-mult number?)
(s/def ::set-x-mult! fn?)

(s/def ::y-mult number?)
(s/def ::set-y-mult! fn?)

(s/def ::show-internal-markers boolean?)
(s/def ::set-show-internal-markers! fn?)

(s/def ::show-scale-gridlines boolean?)
(s/def ::set-show-scale-gridlines! fn?)

(s/def ::show-distance-from-origin boolean?)
(s/def ::set-show-distance-from-origin! fn?)

(s/def ::scale-origin #{:tips :root})
(s/def ::set-scale-origin! fn?)

(s/def ::show-pixel-grid boolean?)
(s/def ::set-show-pixel-grid! fn?)

(s/def ::col-spacing number?)
(s/def ::set-col-spacing! fn?)

(s/def ::highlight-color string?)
(s/def ::set-highlight-color! fn?)

(s/def ::selected-ids (s/nilable set?))
(s/def ::set-selected-ids! fn?)

(s/def ::highlights (s/nilable (s/map-of string? string?)))
(s/def ::set-highlights! fn?)

(s/def ::metadata-panel-collapsed boolean?)
(s/def ::set-metadata-panel-collapsed! fn?)

(s/def ::metadata-panel-height number?)
(s/def ::set-metadata-panel-height! fn?)

(s/def ::metadata-panel-last-drag-height number?)
(s/def ::set-metadata-panel-last-drag-height! fn?)

;; Shape of the context map provided by `app.state/AppStateProvider`.
#_(s/def ::app-state
  (s/keys :req-un [::newick-str ::set-newick-str!
                   ::metadata-rows ::set-metadata-rows!
                   ::active-cols ::set-active-cols!
                   ::x-mult ::set-x-mult!
                   ::y-mult ::set-y-mult!
                   ::show-internal-markers ::set-show-internal-markers!
                   ::show-scale-gridlines ::set-show-scale-gridlines!
                   ::show-distance-from-origin ::set-show-distance-from-origin!
                   ::scale-origin ::set-scale-origin!
                   ::show-pixel-grid ::set-show-pixel-grid!
                   ::col-spacing ::set-col-spacing!
                   ::highlight-color ::set-highlight-color!
                   ::selected-ids ::set-selected-ids!
                   ::highlights ::set-highlights!
                   ::metadata-panel-collapsed ::set-metadata-panel-collapsed!
                   ::metadata-panel-height ::set-metadata-panel-height!
                   ::metadata-panel-last-drag-height ::set-metadata-panel-last-drag-height!]))

;; ===== Component Props =====

(s/def ::columns (s/coll-of ::metadata-header))
(s/def ::start-offset number?)
(s/def ::column-label string?)
(s/def ::cell-height number?)
(s/def ::tip-count nat-int?)
(s/def ::tree-height number?)

(s/def ::sticky-header-props
  (s/keys :req-un [::columns 
                   ::start-offset
                   ::max-depth
                   ::x-scale 
                   ::scale-origin]))

(s/def ::tips (s/coll-of ::positioned-node))
(s/def ::x-offset number?)
(s/def ::y-scale number?)
(s/def ::column-key keyword?)

(s/def ::col-width number?)

(s/def ::metadata-column-props
  (s/keys :req-un [::tips 
                   ::x-offset 
                   ::y-scale 
                   ::column-key 
                   ::column-label 
                   ::cell-height 
                   ::col-width]))

(s/def ::parent-x number?)
(s/def ::parent-y number?)
(s/def ::line-color string?)
(s/def ::line-width number?)

(s/def ::branch-props
  (s/keys :req-un [::x 
                   ::y 
                   ::parent-x 
                   ::parent-y 
                   ::line-color 
                   ::line-width]))

(s/def ::node ::positioned-node)
(s/def ::x-scale number?)

(s/def ::marker-radius number?)
(s/def ::marker-fill string?)
(s/def ::on-toggle-selection (s/nilable fn?))

(s/def ::tree-node-props
  (s/keys :req-un [::node 
                   ::parent-x 
                   ::parent-y 
                   ::x-scale 
                   ::y-scale
                   ::show-internal-markers 
                   ::marker-radius 
                   ::marker-fill
                   ::show-distance-from-origin 
                   ::scale-origin 
                   ::max-depth]
          :opt-un [::highlights 
                   ::selected-ids 
                   ::on-toggle-selection]))

;; Toolbar reads from context — no props spec needed.

(s/def ::tree ::positioned-node)
(s/def ::max-depth number?)
(s/def ::width-px number?)
(s/def ::component-height-px number?)
(s/def ::metadata-panel-collapsed boolean?)
(s/def ::metadata-panel-height number?)
(s/def ::metadata-panel-last-drag-height number?)
(s/def ::set-metadata-panel-height! fn?)
(s/def ::set-metadata-panel-last-drag-height! fn?)

(s/def ::tree-viewer-props
  (s/keys :req-un [::tree 
                   ::tips 
                   ::max-depth 
                   ::x-mult ::y-mult 
                   ::show-internal-markers
                   ::show-scale-gridlines 
                   ::show-pixel-grid
                   ::show-distance-from-origin
                   ::scale-origin
                   ::col-spacing 
                   ::width-px
                   ::component-height-px
                   ::active-cols ::set-active-cols!
                   ::metadata-rows ::set-metadata-rows!
                   ::set-selected-ids!
                   ::metadata-panel-collapsed
                   ::metadata-panel-height
                   ::metadata-panel-last-drag-height
                   ::set-metadata-panel-height!
                   ::set-metadata-panel-last-drag-height! ]
          :opt-un [::highlights ::selected-ids]))

(s/def ::phylogenetic-tree-props
  (s/keys :req-un [::tree 
                   ::x-scale 
                   ::y-scale
                   ::show-internal-markers
                   ::marker-radius 
                   ::marker-fill
                   ::show-distance-from-origin 
                   ::scale-origin
                   ::max-depth]
          :opt-un [::highlights 
                   ::selected-ids 
                   ::on-toggle-selection]))

(s/def ::scale-gridlines-props
  (s/keys :req-un [::max-depth 
                   ::x-scale 
                   ::tree-height
                   ::scale-origin]))

(s/def ::metadata-table-props
  (s/keys :req-un [::active-cols 
                   ::tips 
                   ::start-offset 
                   ::y-scale 
                   ::col-spacing]))

;; TreeContainer receives only layout dimensions.

(s/def ::tree-container-props
  (s/keys :req-un [::width-px 
                   ::component-height-px]))

;; MetadataGrid — AG-Grid table with bidirectional selection sync.

(s/def ::on-cell-edited fn?)
(s/def ::on-cols-reordered fn?)
(s/def ::on-selection-changed fn?)

(s/def ::metadata-grid-props
  (s/keys :req-un [::metadata-rows 
                   ::active-cols 
                   ::tips
                   ::on-cols-reordered 
                   ::on-selection-changed]
          :opt-un [::selected-ids 
                   ::on-cell-edited]))

;; ResizablePanel — wrapper with draggable resize handle.

(s/def ::initial-height number?)
(s/def ::min-height number?)
(s/def ::max-height number?)
(s/def ::height number?)
(s/def ::on-height-change fn?)

(s/def ::resizable-panel-props
  (s/keys :req-un [::initial-height 
                   ::min-height 
                   ::max-height]
          :opt-un [::height ::on-height-change]))

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

(s/fdef app.csv/parse-date
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

(s/fdef app.tree/calculate-scale-unit
  :args (s/cat :max-x pos?)
  :ret  pos?)

(s/fdef app.tree/assign-node-ids
  :args (s/cat :node ::tree-node)
  :ret  ::positioned-node)

(s/fdef app.tree/prepare-tree
  :args (s/cat :newick-str string?
               :metadata-rows (s/coll-of ::metadata-row)
               :active-cols (s/coll-of ::metadata-header))
  :ret  (s/keys :req-un [::tree ::tips ::max-depth]))
