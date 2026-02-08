(ns app.specs
  "Specs for the core data structures and function contracts in Phylo.

  Defines specs for Newick tree nodes, positioned tree nodes,
  metadata structures, UI component props, and the shared app state
  context. These specs serve as living documentation and can be used
  for validation and generative testing.

  Require this namespace in the REPL or in dev preloads to
  enable `cljs.spec.test.alpha/instrument` on key functions."
  (:require [cljs.spec.alpha :as s]))

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

(s/def ::newick-str string?)
(s/def ::metadata-rows (s/coll-of ::metadata-row :kind vector?))
(s/def ::active-cols (s/coll-of ::metadata-header :kind vector?))
(s/def ::x-mult number?)
(s/def ::y-mult number?)

(s/def ::set-newick-str! fn?)
(s/def ::set-metadata-rows! fn?)
(s/def ::set-active-cols! fn?)
(s/def ::set-x-mult! fn?)
(s/def ::set-y-mult! fn?)

(s/def ::show-internal-markers boolean?)
(s/def ::set-show-internal-markers! fn?)

(s/def ::show-scale-gridlines boolean?)
(s/def ::set-show-scale-gridlines! fn?)

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

;; Shape of the context map provided by `app.state/AppStateProvider`.
(s/def ::app-state
  (s/keys :req-un [::newick-str ::set-newick-str!
                   ::metadata-rows ::set-metadata-rows!
                   ::active-cols ::set-active-cols!
                   ::x-mult ::set-x-mult!
                   ::y-mult ::set-y-mult!
                   ::show-internal-markers ::set-show-internal-markers!
                   ::show-scale-gridlines ::set-show-scale-gridlines!
                   ::show-pixel-grid ::set-show-pixel-grid!
                   ::col-spacing ::set-col-spacing!
                   ::highlight-color ::set-highlight-color!
                   ::selected-ids ::set-selected-ids!
                   ::highlights ::set-highlights!]))

;; ===== Component Props =====

(s/def ::columns (s/coll-of ::metadata-header))
(s/def ::start-offset number?)
(s/def ::column-label string?)
(s/def ::cell-height number?)
(s/def ::tip-count nat-int?)
(s/def ::tree-height number?)

(s/def ::sticky-header-props
  (s/keys :req-un [::columns ::start-offset]))

(s/def ::tips (s/coll-of ::positioned-node))
(s/def ::x-offset number?)
(s/def ::y-scale number?)
(s/def ::column-key keyword?)

(s/def ::col-width number?)

(s/def ::metadata-column-props
  (s/keys :req-un [::tips ::x-offset ::y-scale ::column-key ::column-label ::cell-height ::col-width]))

(s/def ::parent-x number?)
(s/def ::parent-y number?)
(s/def ::line-color string?)
(s/def ::line-width number?)

(s/def ::branch-props
  (s/keys :req-un [::x ::y ::parent-x ::parent-y ::line-color ::line-width]))

(s/def ::node ::positioned-node)
(s/def ::x-scale number?)

(s/def ::marker-radius number?)
(s/def ::marker-fill string?)
(s/def ::on-toggle-selection (s/nilable fn?))

(s/def ::tree-node-props
  (s/keys :req-un [::node ::parent-x ::parent-y ::x-scale ::y-scale
                   ::show-internal-markers ::marker-radius ::marker-fill]
          :opt-un [::highlights ::selected-ids ::on-toggle-selection]))

;; Toolbar and SelectionBar read from context — no props spec needed.

(s/def ::tree ::positioned-node)
(s/def ::max-depth number?)
(s/def ::width-px number?)
(s/def ::component-height-px number?)

(s/def ::tree-viewer-props
  (s/keys :req-un [::tree ::tips ::max-depth ::active-cols
                   ::x-mult ::y-mult ::show-internal-markers
                   ::show-scale-gridlines ::show-pixel-grid
                   ::col-spacing ::metadata-rows
                   ::width-px ::component-height-px
                   ::set-active-cols! ::set-selected-ids! ::set-metadata-rows!]
          :opt-un [::highlights ::selected-ids]))

(s/def ::phylogenetic-tree-props
  (s/keys :req-un [::tree ::x-scale ::y-scale
                   ::show-internal-markers
                   ::marker-radius ::marker-fill]
          :opt-un [::highlights ::selected-ids ::on-toggle-selection]))

(s/def ::scale-gridlines-props
  (s/keys :req-un [::max-depth ::x-scale ::tree-height]))

(s/def ::metadata-table-props
  (s/keys :req-un [::active-cols ::tips ::start-offset ::y-scale ::col-spacing]))

;; TreeContainer receives only layout dimensions.

(s/def ::tree-container-props
  (s/keys :req-un [::width-px ::component-height-px]))

;; MetadataGrid — AG-Grid table with bidirectional selection sync.

(s/def ::on-cell-edited fn?)
(s/def ::on-cols-reordered fn?)
(s/def ::on-selection-changed fn?)

(s/def ::metadata-grid-props
  (s/keys :req-un [::metadata-rows ::active-cols ::tips
                   ::on-cols-reordered ::on-selection-changed]
          :opt-un [::selected-ids ::on-cell-edited]))

;; ResizablePanel — wrapper with draggable resize handle.

(s/def ::initial-height number?)
(s/def ::min-height number?)
(s/def ::max-height number?)

(s/def ::resizable-panel-props
  (s/keys :req-un [::initial-height ::min-height ::max-height]))

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
