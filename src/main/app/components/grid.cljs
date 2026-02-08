(ns app.components.grid
  "AG-Grid-based metadata table component.

  Renders an interactive data grid below the tree visualization,
  populated from the same metadata loaded via the toolbar.
  Provides sorting and column-level filtering out of the box.
  Rows default to tree traversal order (matching the SVG tip order).

  Column types detected by [[app.csv/detect-column-type]] are mapped
  to AG-Grid filter types for type-appropriate filtering and sorting."
  (:require [uix.core :as uix :refer [defui $]]
            [ag-grid-community :refer [ModuleRegistry AllCommunityModule themeBalham]]
            [ag-grid-react :refer [AgGridReact]]))

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

  - `:string`  → text filter
  - `:numeric` → number filter with numeric valueGetter for correct sorting
  - `:date`    → date filter with a YYYY-MM-DD comparator"
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
  and value handling. Enables sorting on every column."
  [active-cols]
  (mapv (fn [{:keys [key label type]}]
          (let [field-name (name key)]
            (merge {:field field-name
                    :headerName label
                    :sortable true
                    :resizable true}
                   (col-def-for-type type field-name))))
        active-cols))

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

(defui MetadataGrid
  "Renders an AG-Grid table from metadata rows and column configs.

  Only renders when metadata is loaded (active-cols is non-empty).
  Rows are ordered to match the tree tip traversal order by default.
  Users can sort by clicking column headers; clicking a third time
  clears the sort and reverts to tree order.

  Column filters are type-aware:
  - Text columns get text contains/equals filters
  - Numeric columns get greater-than/less-than/range filters
  - Date columns get a date picker with range filtering

  Props:
  - `:metadata-rows` - vector of maps (keyword keys -> string values)
  - `:active-cols`   - vector of column config maps from CSV parser
  - `:tips`          - enriched leaf nodes in tree display order"
  [{:keys [metadata-rows active-cols tips]}]
  (let [id-key (-> active-cols first :key)
        ordered-rows (uix/use-memo
                      (fn [] (tree-ordered-rows tips metadata-rows id-key))
                      [tips metadata-rows id-key])
        col-defs (uix/use-memo
                  (fn [] (clj->js (cols->col-defs active-cols)))
                  [active-cols])
        row-data (uix/use-memo
                  (fn [] (clj->js ordered-rows))
                  [ordered-rows])]
    (when (seq active-cols)
      ($ :div {:style {:width "100%"
                       :height "100%"
                       :min-height "200px"}}
         ($ AgGridReact
            {:rowData row-data
             :columnDefs col-defs
             :theme themeBalham
             :onFirstDataRendered (fn [params]
                                    (-> params .-api .sizeColumnsToFit))})))))
