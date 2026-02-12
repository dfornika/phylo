(ns app.components.grid
  "AG-Grid-based metadata table component.

  Renders an interactive data grid below the tree visualization,
  populated from the same metadata loaded via the toolbar.
  Provides sorting, column-level filtering, row selection,
  and inline cell editing out of the box.

  Rows default to tree traversal order (matching the SVG tip order).

  Column types detected by [[app.csv/detect-column-type]] are mapped
  to AG-Grid filter types for type-appropriate filtering and sorting.

  Reordering columns in the grid (via drag-and-drop) updates
  `active-cols`, which in turn reorders the SVG metadata columns.

  Row selection is bidirectional: clicking tree leaves updates grid
  checkboxes, and clicking grid checkboxes updates tree markers.

  All data columns (except the ID column) are editable. Edits are
  propagated back to `metadata-rows` via the `:on-cell-edited`
  callback, which causes both the grid and the SVG metadata overlay
  to update in sync."
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [defui $]]
            [ag-grid-community :refer [ModuleRegistry AllCommunityModule themeBalham]]
            [ag-grid-react :refer [AgGridReact]]
            [app.specs :as specs])
  (:require-macros [app.specs :refer [defui-with-spec]]))

;; Register AG-Grid community modules once at namespace load time.
(.registerModules ModuleRegistry #js [AllCommunityModule])

(defn- date-comparator
  "Comparator for AG-Grid's date filter.

  Parses the cell value as a YYYY-MM-DD string and compares it to
  the filter date supplied by AG-Grid. Returns -1, 0, or 1."
  [filter-date cell-value]
  (if (or (nil? cell-value) (= "" cell-value))
    -1
    (let [cell-date (js/Date. cell-value)
          cell-ts (.getTime cell-date)
          filter-ts (.getTime filter-date)]
      (cond
        (< cell-ts filter-ts) -1
        (> cell-ts filter-ts) 1
        :else 0))))

(defn- col-def-for-type
  "Returns AG-Grid columnDef properties appropriate for the detected column type.

  - `:string`  -> text filter
  - `:numeric` -> number filter with numeric valueGetter for correct sorting
  - `:date`    -> date filter with a YYYY-MM-DD comparator"
  [col-type field-name]
  (case col-type
    :numeric {:filter "agNumberColumnFilter"
              :valueGetter (fn [params]
                             (let [v (aget (.-data params) field-name)]
                               (when (and v (not= "" v))
                                 (js/parseFloat v))))}
    :date    {:filter "agDateColumnFilter"
              :filterParams {:comparator date-comparator}}
    ;; default: :string or anything else
    {:filter "agTextColumnFilter"}))

(defn- cols->col-defs
  "Converts active-cols (vector of `{:key :label :width :type}` maps)
  into AG-Grid columnDefs format.

  Maps the detected column type to the appropriate AG-Grid filter
  and value handling. Enables sorting on every column. All columns
  except the first (ID) column are editable."
  [active-cols]
  (let [id-field (some-> active-cols first :key name)]
    (mapv (fn [{:keys [key label type]}]
            (let [field-name (name key)]
              (merge {:field field-name
                      :headerName label
                      :sortable true
                      :resizable true
                      :editable (not= field-name id-field)}
                     (col-def-for-type type field-name))))
          active-cols)))

(defn- tree-ordered-rows
  "Reorders metadata-rows to match the tree tip traversal order.

  Builds an index from the ID column values to the original rows,
  then walks the tips vector (which is in tree display order) and
  collects the matching metadata row for each tip. Tips without
  a matching metadata row are skipped."
  [tips metadata-rows id-key]
  (if (and id-key (seq tips) (seq metadata-rows))
    (let [row-index (into {} (map (fn [r] [(get r id-key) r]) metadata-rows))]
      (into [] (keep (fn [tip] (get row-index (:name tip)))) tips))
    metadata-rows))

(defn- sync-col-order!
  "Reads the current column order from the AG-Grid API and updates
  active-cols to match. Called on the `onDragStopped` event so the
  SVG metadata columns mirror the grid column arrangement."
  [active-cols on-cols-reordered params]
  (let [api (.-api params)
        col-state (.getColumnState api)
        field-order (mapv #(.-colId %) col-state)
        col-index (into {} (map (fn [col] [(name (:key col)) col]) active-cols))
        reordered (into [] (keep #(get col-index %)) field-order)]
    (when (and (seq reordered)
               (not= (mapv :key reordered) (mapv :key active-cols)))
      (on-cols-reordered reordered))))

(defn- sync-selection!
  "Reads the selected rows from the AG-Grid API and updates
  selected-ids. Called on the `onSelectionChanged` event.
  Skips the update when `syncing-ref` is true (programmatic sync)."
  [id-field on-selection-changed syncing-ref params]
  (when-not @syncing-ref
    (let [api (.-api params)
          selected-rows (.getSelectedRows api)
          ids (into #{} (map #(aget % id-field)) selected-rows)]
      (on-selection-changed ids))))

(defn- handle-cell-value-changed
  "Propagates an AG-Grid cell edit to the `on-cell-edited` callback.
  Extracts the row ID, field keyword, and new string value from the
  AG-Grid event params."
  [id-field on-cell-edited ^js params]
  (let [data (.-data params)
        field (.. params -colDef -field)
        new-value (.-newValue params)
        id-value (aget data id-field)]
    (when (and on-cell-edited id-value field)
      (on-cell-edited id-value (keyword field) (str (or new-value ""))))))

(s/def :app.specs/metadata-grid-props
  (s/keys :req-un [:app.specs/metadata-rows
                   :app.specs/active-cols
                   :app.specs/tips
                   :app.specs/on-cols-reordered
                   :app.specs/on-selection-changed]
          :opt-un [:app.specs/selected-ids
                   :app.specs/on-cell-edited]))

(defui MetadataGrid*
  "Renders an AG-Grid table from metadata rows and column configs.

  Only renders when metadata is loaded (active-cols is non-empty).
  Rows are ordered to match the tree tip traversal order by default.
  Users can sort by clicking column headers; clicking a third time
  clears the sort and reverts to tree order.

  Column filters are type-aware:
  - Text columns get text contains/equals filters
  - Numeric columns get greater-than/less-than/range filters
  - Date columns get a date picker with range filtering

  All data columns (except the ID/first column) support inline editing.
  Double-click a cell to edit; press Enter to commit or Escape to cancel.
  Edits propagate back through `:on-cell-edited` to update both the
  grid and the SVG metadata overlay.

  Row selection is bidirectional:
  - Clicking grid checkboxes updates `selected-ids` in app state
  - External changes to `selected-ids` (e.g. from tree clicks)
    are reflected by programmatically selecting/deselecting rows

  Dragging columns in the grid reorders the SVG metadata columns
  to match via the `:on-cols-reordered` callback.

  Props:
  - `:metadata-rows`          - vector of maps (keyword keys -> string values)
  - `:active-cols`            - vector of column config maps from CSV parser
  - `:tips`                   - enriched leaf nodes in tree display order
  - `:selected-ids`           - current set of selected ID strings (for sync)
  - `:on-cols-reordered`      - callback receiving reordered active-cols vector
  - `:on-selection-changed`   - callback receiving set of selected ID strings
  - `:on-cell-edited`         - callback `(fn [id-value field-kw new-value-str])`"
  [{:keys [metadata-rows active-cols tips selected-ids
           on-cols-reordered on-selection-changed on-cell-edited]}]
  (let [id-key (-> active-cols first :key)
        id-field (some-> id-key name)
        ordered-rows (uix/use-memo
                      (fn [] (tree-ordered-rows tips metadata-rows id-key))
                      [tips metadata-rows id-key])
        col-defs (uix/use-memo
                  (fn [] (clj->js (cols->col-defs active-cols)))
                  [active-cols])
        row-data (uix/use-memo
                  (fn [] (clj->js ordered-rows))
                  [ordered-rows])
        row-selection (uix/use-memo
                       (fn [] (clj->js {:mode "multiRow"
                                        :checkboxes true
                                        :headerCheckbox true :selectAll "filtered"}))
                       [])
        ;; Stable row identity so AG-Grid can diff rowData updates
        ;; without resetting scroll position or edit state.
        get-row-id (uix/use-callback
                    (fn [^js params]
                      (when id-field
                        (aget (.-data params) id-field)))
                    [id-field])
        ;; Ref to the AG-Grid API (set on onGridReady)
        grid-api-ref (uix/use-ref nil)
        ;; Guard ref to prevent circular updates:
        ;; true while we're programmatically syncing selection -> grid
        syncing-ref (uix/use-ref false)]

    ;; Sync selected-ids -> grid row selection when selected-ids
    ;; changes externally (e.g. from tree clicks)
    (uix/use-effect
     (fn []
       (when-let [api @grid-api-ref]
         (when id-field
           (reset! syncing-ref true)
           (let [ids (or selected-ids #{})]
             (.forEachNode api
                           (fn [^js node]
                             (when-let [data (.-data node)]
                               (let [row-id (aget data id-field)
                                     should-select (contains? ids row-id)
                                     currently-selected (.isSelected node)]
                                 (when (not= should-select currently-selected)
                                   (.setSelected node should-select)))))))
           ;; Clear the guard after the event loop settles
           (js/setTimeout (fn [] (reset! syncing-ref false)) 0))))
     [selected-ids id-field])

    (when (seq active-cols)
      ($ :div {:style {:width "100%"
                       :height "100%"
                       :min-height "200px"}}
         ($ AgGridReact
            {:rowData row-data
             :columnDefs col-defs
             :theme themeBalham
             :rowSelection row-selection
             :getRowId get-row-id
             :onGridReady (fn [params]
                            (reset! grid-api-ref (.-api params)))
             :onFirstDataRendered (fn [params]
                                    (-> params .-api .sizeColumnsToFit))
             :onDragStopped (fn [params]
                              (sync-col-order! active-cols on-cols-reordered params))
             :onSelectionChanged (fn [params]
                                   (when (and id-field on-selection-changed)
                                     (sync-selection! id-field on-selection-changed syncing-ref params)))
             :onCellValueChanged (fn [params]
                                   (when (and id-field on-cell-edited)
                                     (handle-cell-value-changed id-field on-cell-edited params)))})))))


(defui-with-spec MetadataGrid
  [{:spec :app.specs/metadata-grid-props :props props}]
  ($ MetadataGrid* props))
#_(def MetadataGrid MetadataGrid*)