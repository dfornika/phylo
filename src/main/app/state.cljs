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
  - [[!show-distance-from-origin]]   - whether to show internal node distance labels
  - [[!scale-origin]]          - scale origin for labels (:tips or :root)
  - [[!show-pixel-grid]]       - whether to show pixel-coordinate debug grid
  - [[!col-spacing]]           - extra horizontal spacing between metadata columns
  - [[!highlight-color]]       - CSS color string used as the current brush color
  - [[!selected-ids]]          - set of leaf IDs selected in the AG-Grid
  - [[!highlights]]            - map of {leaf-id -> color} for persistent highlights"
  (:require [uix.core :as uix :refer [defui $]]))

;; ===== Default Data =====

(def default-tree
  "Initial value for the Newick tree string.
  nil means no tree loaded; the UI shows a placeholder."
  nil)

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

;; "Atom holding whether to display internal node distance labels."
(defonce !show-distance-from-origin
  (atom false))

;; "Atom holding scale origin for labels (:tips or :root)."
(defonce !scale-origin
  (atom :tips))

;; "Atom holding whether to display a pixel-coordinate debug grid over the SVG canvas."
(defonce !show-pixel-grid
  (atom false))

;; "Atom holding extra horizontal spacing (in pixels) between metadata columns."
(defonce !col-spacing
  (atom 0))

;; "Atom holding the CSS color string used as the current brush color when assigning highlights."
(defonce !highlight-color
  (atom "#4682B4"))

;; "Atom holding the set of leaf IDs currently selected in the AG-Grid."
(defonce !selected-ids
  (atom #{}))

;; "Atom holding persistent per-leaf highlight colors as {id-string -> color-string}."
(defonce !highlights
  (atom {}))

;; ===== Export / Import =====

(def ^:private export-version
  "Current export schema version for standalone HTML export payloads."
  1)

(def ^:private export-defaults
  {:newick-str default-tree
   :metadata-rows []
   :active-cols []
   :x-mult 0.5
   :y-mult 30
   :show-internal-markers false
   :show-scale-gridlines false
   :show-distance-from-origin false
   :scale-origin :tips
   :show-pixel-grid false
   :col-spacing 0
   :highlight-color "#4682B4"
   :selected-ids #{}
   :highlights {}})

(defn export-state
  "Returns a versioned, EDN-serializable snapshot of app state.

  Intended for embedding into standalone HTML exports."
  []
  {:version export-version
   :state {:newick-str @!newick-str
           :metadata-rows @!metadata-rows
           :active-cols @!active-cols
           :x-mult @!x-mult
           :y-mult @!y-mult
           :show-internal-markers @!show-internal-markers
           :show-scale-gridlines @!show-scale-gridlines
           :show-distance-from-origin @!show-distance-from-origin
           :scale-origin @!scale-origin
           :show-pixel-grid @!show-pixel-grid
           :col-spacing @!col-spacing
           :highlight-color @!highlight-color
           :selected-ids @!selected-ids
           :highlights @!highlights}})

(defn- normalize-export
  "Normalizes export payloads to a flat state map.

  Accepts either the full {:version :state} wrapper or a raw state map."
  [payload]
  (cond
    (and (map? payload) (map? (:state payload))) (:state payload)
    (map? payload) payload
    :else nil))

(defn- coerce-set
  "Coerces a value to a set, returning empty set when invalid."
  [value]
  (cond
    (set? value) value
    (sequential? value) (set value)
    :else #{}))

(defn apply-export-state!
  "Applies an exported state payload into the live atoms.

  Missing keys fall back to defaults so exports remain forward-compatible."
  [payload]
  (when-let [state-map (normalize-export payload)]
    (let [merged (merge export-defaults state-map)
          merged (if (contains? state-map :show-branch-lengths)
                   (assoc merged :show-distance-from-origin (:show-branch-lengths state-map))
                   merged)]
      (reset! !newick-str (:newick-str merged))
      (reset! !metadata-rows (:metadata-rows merged))
      (reset! !active-cols (:active-cols merged))
      (reset! !x-mult (:x-mult merged))
      (reset! !y-mult (:y-mult merged))
      (reset! !show-internal-markers (:show-internal-markers merged))
      (reset! !show-scale-gridlines (:show-scale-gridlines merged))
      (reset! !show-distance-from-origin (:show-distance-from-origin merged))
      (reset! !scale-origin (:scale-origin merged))
      (reset! !show-pixel-grid (:show-pixel-grid merged))
      (reset! !col-spacing (:col-spacing merged))
      (reset! !highlight-color (:highlight-color merged))
      (reset! !selected-ids (coerce-set (:selected-ids merged)))
      (reset! !highlights (if (map? (:highlights merged))
                            (:highlights merged)
                            {})))))

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
        show-distance-from-origin   (uix/use-atom !show-distance-from-origin)
        scale-origin          (uix/use-atom !scale-origin)
        show-pixel-grid       (uix/use-atom !show-pixel-grid)
        col-spacing           (uix/use-atom !col-spacing)
        highlight-color       (uix/use-atom !highlight-color)
        selected-ids          (uix/use-atom !selected-ids)
        highlights            (uix/use-atom !highlights)]
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
                            :show-distance-from-origin show-distance-from-origin
                            :set-show-distance-from-origin! #(reset! !show-distance-from-origin %)
                            :scale-origin scale-origin
                            :set-scale-origin! #(reset! !scale-origin %)
                            :show-pixel-grid show-pixel-grid
                            :set-show-pixel-grid! #(reset! !show-pixel-grid %)
                            :col-spacing col-spacing
                            :set-col-spacing! #(reset! !col-spacing %)
                            :highlight-color highlight-color
                            :set-highlight-color! #(reset! !highlight-color %)
                            :selected-ids selected-ids
                            :set-selected-ids! #(if (fn? %) (swap! !selected-ids %) (reset! !selected-ids %))
                            :highlights highlights
                            :set-highlights! #(reset! !highlights %)}}
       children)))

(defn use-app-state
  "Convenience hook returning the app state map from context.

  Must be called inside a component wrapped by [[AppStateProvider]].
  Returns a map with all state values and setter functions — see
  [[AppStateProvider]] for the full key listing."
  []
  (uix/use-context app-context))
