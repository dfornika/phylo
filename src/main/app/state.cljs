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
  - [[!metadata-panel-collapsed]] - whether the metadata grid panel is collapsed
  - [[!metadata-panel-height]] - current height of the metadata grid panel
  - [[!metadata-panel-last-drag-height]] - last height set via drag
  - [[!highlight-color]]       - CSS color string used as the current brush color
  - [[!selected-ids]]          - set of leaf IDs selected in the AG-Grid
  - [[!highlights]]            - map of {leaf-id -> color} for persistent highlights
  - [[!legend-pos]]            - top-left legend position in SVG user space
  - [[!legend-collapsed?]]     - whether the legend is collapsed
  - [[!legend-labels]]         - map of {color-hex -> label} for custom colors
  - [[!legend-visible?]]       - whether the legend is visible"
  (:require [cljs.spec.alpha :as s]
            [app.specs :as specs]
            [uix.core :as uix :refer [defui $]]))

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

;; "Atom holding whether the metadata grid panel is collapsed."
(defonce !metadata-panel-collapsed
  (atom true))

;; "Atom holding the current height of the metadata grid panel."
(defonce !metadata-panel-height
  (atom 250))

;; "Atom holding the last height set by dragging the panel."
(defonce !metadata-panel-last-drag-height
  (atom 250))

;; "Atom holding the CSS color string used as the current brush color when assigning highlights."
(defonce !highlight-color
  (atom "#4682B4"))

;; "Atom holding the set of leaf IDs currently selected in the AG-Grid."
(defonce !selected-ids
  (atom #{}))

;; "Atom holding persistent per-leaf highlight colors as {id-string -> color-string}."
(defonce !highlights
  (atom {}))

;; "Atom holding whether metadata-based coloring is enabled."
(defonce !color-by-enabled?
  (atom false))

;; "Atom holding the active metadata field keyword for auto-coloring."
(defonce !color-by-field
  (atom nil))

;; "Atom holding the palette id keyword for auto-coloring."
(defonce !color-by-palette
  (atom :bright))

;; "Atom holding the type override for auto-coloring (:auto, :categorical, :numeric, :date)."
(defonce !color-by-type-override
  (atom :auto))

;; "Atom holding the top-left legend position in SVG user space."
(defonce !legend-pos
  (atom nil))

;; "Atom holding whether the legend is collapsed."
(defonce !legend-collapsed?
  (atom false))

;; "Atom holding label overrides for custom colors as {color-hex -> label}."
(defonce !legend-labels
  (atom {}))

;; "Atom holding whether the legend is visible."
(defonce !legend-visible?
  (atom false))

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
   :metadata-panel-collapsed true
   :metadata-panel-height 250
   :metadata-panel-last-drag-height 250
   :highlight-color "#4682B4"
   :selected-ids #{}
   :highlights {}
   :color-by-enabled? false
   :color-by-field nil
   :color-by-palette :bright
   :color-by-type-override :auto
   :legend-pos nil
   :legend-collapsed? false
   :legend-labels {}
   :legend-visible? false})

(defn export-state
  "Returns a versioned, EDN-serializable snapshot of app state.

  Intended for embedding into standalone HTML exports."
  []
  {:version export-version
   :state {:newick-str      @!newick-str
           :metadata-rows   @!metadata-rows
           :active-cols     @!active-cols
           :x-mult          @!x-mult
           :y-mult          @!y-mult
           :show-internal-markers     @!show-internal-markers
           :show-scale-gridlines      @!show-scale-gridlines
           :show-distance-from-origin @!show-distance-from-origin
           :scale-origin              @!scale-origin
           :show-pixel-grid           @!show-pixel-grid
           :col-spacing               @!col-spacing
           :metadata-panel-collapsed  @!metadata-panel-collapsed
           :metadata-panel-height     @!metadata-panel-height
           :metadata-panel-last-drag-height @!metadata-panel-last-drag-height
           :highlight-color @!highlight-color
           :selected-ids    @!selected-ids
           :highlights      @!highlights
           :color-by-enabled? @!color-by-enabled?
           :color-by-field     @!color-by-field
           :color-by-palette   @!color-by-palette
           :color-by-type-override @!color-by-type-override
           :legend-pos @!legend-pos
           :legend-collapsed? @!legend-collapsed?
           :legend-labels @!legend-labels
           :legend-visible? @!legend-visible?}})

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

(defn- coerce-palette
  "Coerces a palette value to a valid palette keyword.
  
  Returns `:bright` (the default) if the value is invalid or missing.
  Valid palettes are: :bright, :contrast, :pastel (categorical),
  :blue-red, :teal-gold (gradient)."
  [value]
  (if (#{:bright :contrast :pastel :blue-red :teal-gold} value)
    value
    :bright))

(defn- coerce-type-override
  "Coerces a type override value to a valid keyword.
  
  Returns `:auto` (the default) if the value is invalid or missing.
  Valid values are: :auto, :categorical, :numeric, :date."
  [value]
  (if (#{:auto :categorical :numeric :date} value)
    value
    :auto))

(defn- coerce-legend-pos
  "Coerces a legend position map to {:x :y} or nil."
  [value]
  (when (and (map? value) (number? (:x value)) (number? (:y value)))
    {:x (:x value) :y (:y value)}))

(defn- coerce-legend-labels
  "Coerces legend label overrides into a map."
  [value]
  (if (map? value) value {}))

(defn- coerce-legend-visible
  "Coerces legend visibility to a boolean."
  [value]
  (boolean value))

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
      (reset! !metadata-panel-collapsed (:metadata-panel-collapsed merged))
      (reset! !metadata-panel-height (:metadata-panel-height merged))
      (reset! !metadata-panel-last-drag-height (:metadata-panel-last-drag-height merged))
      (reset! !highlight-color (:highlight-color merged))
      (reset! !selected-ids (coerce-set (:selected-ids merged)))
      (reset! !highlights (if (map? (:highlights merged))
                            (:highlights merged)
                            {}))
      (reset! !color-by-enabled? (boolean (:color-by-enabled? merged)))
      (reset! !color-by-field (:color-by-field merged))
      (reset! !color-by-palette (coerce-palette (:color-by-palette merged)))
      (reset! !color-by-type-override (coerce-type-override (:color-by-type-override merged)))
      (reset! !legend-pos (coerce-legend-pos (:legend-pos merged)))
      (reset! !legend-collapsed? (boolean (:legend-collapsed? merged)))
      (reset! !legend-labels (coerce-legend-labels (:legend-labels merged)))
      (reset! !legend-visible? (coerce-legend-visible (:legend-visible? merged))))))

;; ===== Context =====

(def app-context
  "React context carrying the app state map.
  Provided by [[AppStateProvider]], consumed via [[use-app-state]]."
  (uix/create-context nil))


(s/def :app.specs/app-state
  (s/keys :req-un [:app.specs/newick-str      :app.specs/set-newick-str!
                   :app.specs/metadata-rows   :app.specs/set-metadata-rows!
                   :app.specs/active-cols     :app.specs/set-active-cols!
                   :app.specs/x-mult          :app.specs/set-x-mult!
                   :app.specs/y-mult          :app.specs/set-y-mult!
                   :app.specs/show-internal-markers           :app.specs/set-show-internal-markers!
                   :app.specs/show-scale-gridlines            :app.specs/set-show-scale-gridlines!
                   :app.specs/show-distance-from-origin       :app.specs/set-show-distance-from-origin!
                   :app.specs/scale-origin    :app.specs/set-scale-origin!
                   :app.specs/show-pixel-grid :app.specs/set-show-pixel-grid!
                   :app.specs/col-spacing     :app.specs/set-col-spacing!
                   :app.specs/highlight-color :app.specs/set-highlight-color!
                   :app.specs/selected-ids    :app.specs/set-selected-ids!
                   :app.specs/highlights      :app.specs/set-highlights!
                   :app.specs/color-by-enabled? :app.specs/set-color-by-enabled!
                   :app.specs/color-by-field    :app.specs/set-color-by-field!
                   :app.specs/color-by-palette  :app.specs/set-color-by-palette!
                   :app.specs/color-by-type-override :app.specs/set-color-by-type-override!
                   :app.specs/legend-pos :app.specs/set-legend-pos!
                   :app.specs/legend-collapsed? :app.specs/set-legend-collapsed!
                   :app.specs/legend-labels :app.specs/set-legend-labels!
                   :app.specs/legend-visible? :app.specs/set-legend-visible!
                   :app.specs/metadata-panel-collapsed        :app.specs/set-metadata-panel-collapsed!
                   :app.specs/metadata-panel-height           :app.specs/set-metadata-panel-height!
                   :app.specs/metadata-panel-last-drag-height :app.specs/set-metadata-panel-last-drag-height!
                   ]))

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
        show-internal-markers       (uix/use-atom !show-internal-markers)
        show-scale-gridlines        (uix/use-atom !show-scale-gridlines)
        show-distance-from-origin   (uix/use-atom !show-distance-from-origin)
        scale-origin                (uix/use-atom !scale-origin)
        show-pixel-grid             (uix/use-atom !show-pixel-grid)
        col-spacing                 (uix/use-atom !col-spacing)
        metadata-panel-collapsed    (uix/use-atom !metadata-panel-collapsed)
        metadata-panel-height       (uix/use-atom !metadata-panel-height)
        metadata-panel-last-drag-height (uix/use-atom !metadata-panel-last-drag-height)
        highlight-color             (uix/use-atom !highlight-color)
        selected-ids                (uix/use-atom !selected-ids)
        highlights                  (uix/use-atom !highlights)
        color-by-enabled?           (uix/use-atom !color-by-enabled?)
        color-by-field              (uix/use-atom !color-by-field)
        color-by-palette            (uix/use-atom !color-by-palette)
        color-by-type-override   (uix/use-atom !color-by-type-override)
        legend-pos                (uix/use-atom !legend-pos)
        legend-collapsed?         (uix/use-atom !legend-collapsed?)
        legend-labels             (uix/use-atom !legend-labels)
        legend-visible?           (uix/use-atom !legend-visible?)
        app-state     {:newick-str           newick-str
                       :set-newick-str!      #(reset! !newick-str %)
                       :metadata-rows        metadata-rows
                       :set-metadata-rows!   #(reset! !metadata-rows %)
                       :active-cols          active-cols
                       :set-active-cols!     #(reset! !active-cols %)
                       :x-mult               x-mult
                       :set-x-mult!          #(reset! !x-mult %)
                       :y-mult               y-mult
                       :set-y-mult!          #(reset! !y-mult %)
                       :show-internal-markers                show-internal-markers
                       :set-show-internal-markers!           #(reset! !show-internal-markers %)
                       :show-scale-gridlines                 show-scale-gridlines
                       :set-show-scale-gridlines!            #(reset! !show-scale-gridlines %)
                       :show-distance-from-origin            show-distance-from-origin
                       :set-show-distance-from-origin!       #(reset! !show-distance-from-origin %)
                       :scale-origin         scale-origin
                       :set-scale-origin!    #(reset! !scale-origin %)
                       :show-pixel-grid      show-pixel-grid
                       :set-show-pixel-grid! #(reset! !show-pixel-grid %)
                       :col-spacing          col-spacing
                       :set-col-spacing!     #(reset! !col-spacing %)
                       :metadata-panel-collapsed             metadata-panel-collapsed
                       :set-metadata-panel-collapsed!        #(reset! !metadata-panel-collapsed %)
                       :metadata-panel-height                metadata-panel-height
                       :set-metadata-panel-height!           #(reset! !metadata-panel-height %)
                       :metadata-panel-last-drag-height      metadata-panel-last-drag-height
                       :set-metadata-panel-last-drag-height! #(reset! !metadata-panel-last-drag-height %)
                       :highlight-color      highlight-color
                       :set-highlight-color! #(reset! !highlight-color %)
                       :selected-ids         selected-ids
                       :set-selected-ids!    #(if (fn? %) (swap! !selected-ids %) (reset! !selected-ids %))
                       :highlights           highlights
                       :set-highlights!      #(reset! !highlights %)
                       :color-by-enabled?    color-by-enabled?
                       :set-color-by-enabled! #(reset! !color-by-enabled? %)
                       :color-by-field       color-by-field
                       :set-color-by-field!  #(reset! !color-by-field %)
                       :color-by-palette     color-by-palette
                       :set-color-by-palette! #(reset! !color-by-palette %)
                       :color-by-type-override color-by-type-override
                       :set-color-by-type-override! #(reset! !color-by-type-override %)
                       :legend-pos legend-pos
                       :set-legend-pos! #(reset! !legend-pos %)
                       :legend-collapsed? legend-collapsed?
                       :set-legend-collapsed! #(reset! !legend-collapsed? %)
                       :legend-labels legend-labels
                       :set-legend-labels! #(reset! !legend-labels %)
                       :legend-visible? legend-visible?
                       :set-legend-visible! #(reset! !legend-visible? %)}]
    (when ^boolean goog.DEBUG
      (specs/validate-spec! app-state :app.specs/app-state "app-state" {:check-unexpected-keys? true}))
    ($ app-context {:value app-state}
       children)))

(defn use-app-state
  "Convenience hook returning the app state map from context.

  Must be called inside a component wrapped by [[AppStateProvider]].
  Returns a map with all state values and setter functions — see
  [[AppStateProvider]] for the full key listing."
  []
  (uix/use-context app-context))
