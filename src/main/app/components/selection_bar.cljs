(ns app.components.selection-bar
  "Selection bar component for assigning highlight colors.

  Renders a compact control bar above the AG-Grid panel that shows
  the current selection count, a color picker for choosing a brush
  color, and buttons to assign/clear colors on selected rows.

  Reads state from React context via [[app.state/use-app-state]]."
  (:require [uix.core :as uix :refer [defui $]]
            [app.csv :as csv]
            [app.export.html :as export-html]
            [app.state :as state]))

(def ^:private navy "#003366")

(def ^:private selectionbar-font
  "System sans-serif font stack for the toolbar."
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif")

(defui SelectionBar
  "Compact bar showing selection count and highlight controls.

  Displays the number of currently selected rows, a color picker
  (bound to `highlight-color`), and action buttons:
  - **Assign Color** — merges selected IDs into `highlights` with the
    current brush color
  - **Clear Color** — removes selected IDs from `highlights`
  - **Clear All Colors** — resets all highlights
  - **Select All** — Selects all leaf nodes
  - **Select None** —  Clears all selections

  Reads all state from context. Accepts optional `:max-panel-height` for expand."
  [{:keys [max-panel-height]}]
  (let [{:keys [selected-ids highlights set-highlights!
                highlight-color set-highlight-color!
                metadata-rows active-cols set-selected-ids!
                metadata-panel-collapsed set-metadata-panel-collapsed!
                metadata-panel-height metadata-panel-last-drag-height
                set-metadata-panel-height!]} (state/use-app-state)
        n-selected (count selected-ids)
        n-highlighted (count highlights)
        button-style {:font-size "11px" :padding "3px 10px"
                      :cursor "pointer" :border "1px solid #bbb"
                      :border-radius "3px" :background "#fff"}
        icon-button-style {:width "22px" :height "22px" :padding 0
                           :font-size "14px" :font-weight "bold" :color navy
                           :cursor "pointer" :border "1px solid #bbb"
                           :border-radius "3px" :background "#fff"}
        max-panel-height (or max-panel-height 0)
        restore-height (or metadata-panel-last-drag-height 250)
        restore-target (if (pos? max-panel-height)
                         (min restore-height max-panel-height)
                         restore-height)
        restore-disabled? (or (not (pos? restore-target))
                               (and (not metadata-panel-collapsed)
                                    (= metadata-panel-height restore-target)))
        maximize-disabled? (or (not (pos? max-panel-height))
                                (and (not metadata-panel-collapsed)
                                     (>= metadata-panel-height max-panel-height)))
        id-key (-> active-cols first :key)
        all-ids (if id-key
                  (into #{} (keep (fn [row] (get row id-key))) metadata-rows)
                  #{})
        select-all-disabled? (not (seq all-ids))
        select-none-disabled? (zero? n-selected)
        export-disabled? (not (seq active-cols))]
    ($ :div {:style {:display "flex" :gap "10px" :padding "4px 8px"
                     :background "#f0f2f5" :border-bottom "1px solid #ccd"
                     :align-items "center" :flex-wrap "wrap"
                     :min-height "24px"
                     :font-family selectionbar-font
                     :color navy}}
       ;; Selection count
       ($ :span {:style {:font-size "12px" :font-weight "bold" :min-width "110px"}}
          (str n-selected " row" (when (not= n-selected 1) "s") " selected"))
       ;; Color picker
       ($ :label {:style {:font-size "11px" :display "flex" :align-items "center" :gap "4px"}}
          "Color:"
          ($ :input {:type "color"
                     :value highlight-color
                     :on-change (fn [e] (set-highlight-color! (.. e -target -value)))
                     :style {:width "28px" :height "22px" :border "none"
                             :padding "0" :cursor "pointer"}}))
       
       ;; Assign button
       ($ :button {:style (merge button-style
                                 (when (zero? n-selected)
                                   {:opacity "0.5" :cursor "default"}))
                   :disabled (zero? n-selected)
                   :on-click (fn [_]
                               (when (pos? n-selected)
                                 (set-highlights!
                                  (into highlights
                                        (map (fn [id] [id highlight-color]))
                                        selected-ids))))}
          "Assign Color")
       ;; Clear color on selection
       ($ :button {:style (merge button-style
                                 (when (zero? n-selected)
                                   {:opacity "0.5" :cursor "default"}))
                   :disabled (zero? n-selected)
                   :on-click (fn [_]
                               (when (pos? n-selected)
                                 (set-highlights!
                                  (apply dissoc highlights selected-ids))))}
          "Clear Color")
       ;; Clear all highlights
       ($ :button {:style (merge button-style
                                 (when (zero? n-highlighted)
                                   {:opacity "0.5" :cursor "default"}))
                   :disabled (zero? n-highlighted)
                   :on-click (fn [_] (set-highlights! {}))}
          "Clear All Colors")
       
       ;; Selection shortcuts
       ($ :button {:style (merge button-style
                                 (when select-all-disabled?
                                   {:opacity "0.5" :cursor "default"}))
                   :disabled select-all-disabled?
                   :on-click (fn [_]
                               (when (seq all-ids)
                                 (set-selected-ids! all-ids)))}
          "Select All")
       ($ :button {:style (merge button-style
                                 (when select-none-disabled?
                                   {:opacity "0.5" :cursor "default"}))
                   :disabled select-none-disabled?
                   :on-click (fn [_] (set-selected-ids! #{}))}
          "Select None")

       ;; Minimize/restore/maximize controls (far right)
       ($ :div {:style {:margin-left "auto" :display "flex" :gap "6px"}}
          ($ :button {:style (merge button-style
                                   {:padding "3px 8px"}
                                   (when export-disabled?
                                     {:opacity "0.5" :cursor "default"}))
                      :title "Download metadata as CSV"
                      :disabled export-disabled?
                      :on-click (fn [_]
                                  (when (seq active-cols)
                                    (let [csv-text (csv/metadata->csv active-cols metadata-rows)
                                          blob (js/Blob. (clj->js [csv-text])
                                                        #js {:type "text/csv;charset=utf-8"})]
                                      (export-html/save-blob!
                                       blob
                                       "metadata.csv"
                                       [{:description "CSV File"
                                         :accept {"text/csv" [".csv"]}}]))))}
             "CSV")
          ($ :button {:style (merge icon-button-style
                                   (when metadata-panel-collapsed
                                     {:opacity "0.4" :cursor "default"}))
                      :title "Collapse metadata grid"
                      :disabled metadata-panel-collapsed
                      :on-click (fn [_] (set-metadata-panel-collapsed! true))}
             "▼")
          ($ :button {:style (merge icon-button-style
                                   (when restore-disabled?
                                     {:opacity "0.4" :cursor "default"}))
                      :title "Restore last dragged height"
                      :disabled restore-disabled?
                      :on-click (fn [_]
                                  (when (pos? restore-target)
                                    (set-metadata-panel-height! restore-target)
                                    (set-metadata-panel-collapsed! false)))}
             "●")
          ($ :button {:style (merge icon-button-style
                                   (when maximize-disabled?
                                     {:opacity "0.4" :cursor "default"}))
                      :title "Expand metadata grid"
                      :disabled maximize-disabled?
                      :on-click (fn [_]
                                  (when (pos? max-panel-height)
                                    (set-metadata-panel-height! max-panel-height)
                                    (set-metadata-panel-collapsed! false)))}
             "▲"))
       )))
