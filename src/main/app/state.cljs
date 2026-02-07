(ns app.state
  "Centralized application state for Phylo.

  All shared mutable state lives in `defonce` atoms so it survives
  shadow-cljs hot reloads. Components access state through a single
  React context ([[app-context]]) provided by [[AppStateProvider]],
  and read it via the [[use-app-state]] convenience hook.

  State atoms:
  - [[!newick-str]]     - the current Newick tree string
  - [[!metadata-rows]]  - parsed metadata rows (vector of maps)
  - [[!active-cols]]    - column header configs for metadata display
  - [[!x-mult]]         - horizontal zoom multiplier
  - [[!y-mult]]         - vertical tip spacing (pixels)
  - [[!show-internal-markers]] - whether to show markers on internal nodes
  - [[!show-scale-gridlines]]  - whether to show scale (distance) gridlines
  - [[!show-pixel-grid]]       - whether to show pixel-coordinate debug grid
  - [[!col-spacing]]           - extra horizontal spacing between metadata columns
  - [[!date-filter-col]]       - keyword for the metadata column used for date filtering
  - [[!date-filter-range]]     - two-element vector of date strings [start end]
  - [[!highlight-color]]       - CSS color string for highlighted leaf markers"
  (:require [uix.core :as uix :refer [defui $]]))

;; ===== Default Data =====

(def default-tree
  "A small demo Newick tree shown on initial load.
  Four leaves (A–D) with branch lengths, providing a minimal
  but visually meaningful example."
  "((A:0.2,B:0.3):0.4,(C:0.5,D:0.1):0.3);")

;; ===== State Atoms =====
;; `defonce` ensures these survive shadow-cljs hot reloads.

;; "Atom holding the current Newick-format tree string."
(defonce !newick-str
  (atom default-tree))

;; "Atom holding parsed metadata rows — a vector of maps keyed by header keywords."
(defonce !metadata-rows
  (atom []))

;; "Atom holding the active metadata column configs — a vector of maps with `:key`, `:label`, and `:width`."
(defonce !active-cols
  (atom []))

;; "Atom holding the horizontal zoom multiplier (0.05–1.5)."
(defonce !x-mult
  (atom 0.5))

;; "Atom holding the vertical tip spacing in pixels (10–100)."
(defonce !y-mult
  (atom 30))

;; "Atom holding whether to display circular markers on internal (non-leaf) nodes."
(defonce !show-internal-markers
  (atom false))

;; "Atom holding whether to display scale (evolutionary distance) gridlines behind the tree."
(defonce !show-scale-gridlines
  (atom false))

;; "Atom holding whether to display a pixel-coordinate debug grid over the SVG canvas."
(defonce !show-pixel-grid
  (atom false))

;; "Atom holding extra horizontal spacing (in pixels) between metadata columns."
(defonce !col-spacing
  (atom 0))

;; "Atom holding the keyword of the metadata column selected for date range filtering, or nil."
(defonce !date-filter-col
  (atom nil))

;; "Atom holding the date range bounds as [start-date end-date] normalized YYYY-MM-DD strings, or nil."
(defonce !date-filter-range
  (atom nil))

;; "Atom holding the CSS color string used to highlight leaf markers within the date range."
(defonce !highlight-color
  (atom "#4682B4"))

;; ===== Context =====

(def app-context
  "React context carrying the app state map.
  Provided by [[AppStateProvider]], consumed via [[use-app-state]]."
  (uix/create-context nil))

(defui AppStateProvider
  "Wraps children with the app state context.

  Subscribes to all state atoms via `uix/use-atom` so that any atom
  change triggers a re-render of consumers. The context value is a
  map of current values and setter functions."
  [{:keys [children]}]
  (let [newick-str     (uix/use-atom !newick-str)
        metadata-rows  (uix/use-atom !metadata-rows)
        active-cols    (uix/use-atom !active-cols)
        x-mult         (uix/use-atom !x-mult)
        y-mult         (uix/use-atom !y-mult)
        show-internal-markers (uix/use-atom !show-internal-markers)
        show-scale-gridlines  (uix/use-atom !show-scale-gridlines)
        show-pixel-grid       (uix/use-atom !show-pixel-grid)
        col-spacing           (uix/use-atom !col-spacing)
        date-filter-col       (uix/use-atom !date-filter-col)
        date-filter-range     (uix/use-atom !date-filter-range)
        highlight-color       (uix/use-atom !highlight-color)]
    ($ app-context {:value {:newick-str       newick-str
                            :set-newick-str!  #(reset! !newick-str %)
                            :metadata-rows    metadata-rows
                            :set-metadata-rows! #(reset! !metadata-rows %)
                            :active-cols      active-cols
                            :set-active-cols! #(reset! !active-cols %)
                            :x-mult           x-mult
                            :set-x-mult!      #(reset! !x-mult %)
                            :y-mult           y-mult
                            :set-y-mult!      #(reset! !y-mult %)
                            :show-internal-markers show-internal-markers
                            :set-show-internal-markers! #(reset! !show-internal-markers %)
                            :show-scale-gridlines show-scale-gridlines
                            :set-show-scale-gridlines! #(reset! !show-scale-gridlines %)
                            :show-pixel-grid show-pixel-grid
                            :set-show-pixel-grid! #(reset! !show-pixel-grid %)
                            :col-spacing col-spacing
                            :set-col-spacing! #(reset! !col-spacing %)
                            :date-filter-col date-filter-col
                            :set-date-filter-col! #(reset! !date-filter-col %)
                            :date-filter-range date-filter-range
                            :set-date-filter-range! #(reset! !date-filter-range %)
                            :highlight-color highlight-color
                            :set-highlight-color! #(reset! !highlight-color %)}}
       children)))

(defn use-app-state
  "Convenience hook returning the app state map from context.

  Must be called inside a component wrapped by [[AppStateProvider]].
  Returns a map with all state values and setter functions — see
  [[AppStateProvider]] for the full key listing."
  []
  (uix/use-context app-context))
