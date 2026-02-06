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

(s/def ::positioned-node
  (s/keys :req-un [::name ::branch-length ::children ::x ::y]))

;; ===== Metadata Structures =====

(s/def ::key keyword?)
(s/def ::label string?)
(s/def ::width number?)

(s/def ::metadata-header
  (s/keys :req-un [::key ::label ::width]))

(s/def ::metadata-row
  (s/map-of keyword? string?))

(s/def ::headers (s/coll-of ::metadata-header))
(s/def ::data (s/coll-of ::metadata-row))

(s/def ::parsed-metadata
  (s/keys :req-un [::headers ::data]))

;; ===== App State Context =====

(s/def ::newick-str string?)
(s/def ::metadata-rows (s/coll-of ::metadata-row))
(s/def ::active-cols (s/coll-of ::metadata-header))
(s/def ::x-mult number?)
(s/def ::y-mult number?)

(s/def ::set-newick-str! fn?)
(s/def ::set-metadata-rows! fn?)
(s/def ::set-active-cols! fn?)
(s/def ::set-x-mult! fn?)
(s/def ::set-y-mult! fn?)

(s/def ::app-state
  "Shape of the context map provided by `app.state/AppStateProvider`."
  (s/keys :req-un [::newick-str ::set-newick-str!
                   ::metadata-rows ::set-metadata-rows!
                   ::active-cols ::set-active-cols!
                   ::x-mult ::set-x-mult!
                   ::y-mult ::set-y-mult!]))

;; ===== Component Props =====

(s/def ::columns (s/coll-of ::metadata-header))
(s/def ::start-offset number?)

(s/def ::metadata-header-props
  (s/keys :req-un [::columns ::start-offset]))

(s/def ::tips (s/coll-of ::positioned-node))
(s/def ::x-offset number?)
(s/def ::y-scale number?)
(s/def ::column-key keyword?)

(s/def ::metadata-column-props
  (s/keys :req-un [::tips ::x-offset ::y-scale ::column-key]))

(s/def ::parent-x number?)
(s/def ::parent-y number?)
(s/def ::line-color string?)
(s/def ::line-width number?)

(s/def ::branch-props
  (s/keys :req-un [::x ::y ::parent-x ::parent-y ::line-color ::line-width]))

(s/def ::node ::positioned-node)
(s/def ::x-scale number?)

(s/def ::tree-node-props
  (s/keys :req-un [::node ::parent-x ::parent-y ::x-scale ::y-scale]))

;; Toolbar reads from context — no props spec needed.
;; PhylogeneticTree receives only layout dimensions.

(s/def ::width-px number?)
(s/def ::component-height-px number?)

(s/def ::phylogenetic-tree-props
  (s/keys :req-un [::width-px ::component-height-px]))

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

(s/fdef app.core/count-tips
  :args (s/cat :node ::tree-node)
  :ret  pos-int?)

(s/fdef app.core/get-max-x
  :args (s/cat :node ::positioned-node)
  :ret  number?)

(s/fdef app.core/get-leaves
  :args (s/cat :node ::positioned-node)
  :ret  (s/coll-of ::positioned-node))

(s/fdef app.core/calculate-scale-unit
  :args (s/cat :max-x pos?)
  :ret  pos?)
