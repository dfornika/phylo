(ns app.components.selection-bar
  "Selection bar component for assigning highlight colors.

  Renders a compact control bar above the AG-Grid panel that shows
  the current selection count, a color picker for choosing a brush
  color, and buttons to assign/clear colors on selected rows.

  Reads state from React context via [[app.state/use-app-state]]."
  (:require [uix.core :as uix :refer [defui $]]
            [app.state :as state]))

(defui SelectionBar
  "Compact bar showing selection count and highlight controls.

  Displays the number of currently selected rows, a color picker
  (bound to `highlight-color`), and action buttons:
  - **Assign Color** — merges selected IDs into `highlights` with the
    current brush color
  - **Clear Color** — removes selected IDs from `highlights`
  - **Clear All** — resets all highlights

  Reads all state from context — requires no props."
  [_props]
  (let [{:keys [selected-ids highlights set-highlights!
                highlight-color set-highlight-color!]} (state/use-app-state)
        n-selected (count selected-ids)
        n-highlighted (count highlights)
        button-style {:font-size "11px" :padding "3px 10px"
                      :cursor "pointer" :border "1px solid #bbb"
                      :border-radius "3px" :background "#fff"}]
    ($ :div {:style {:display "flex" :gap "10px" :padding "6px 12px"
                     :background "#e8f0fe" :border-bottom "1px solid #ccd"
                     :align-items "center" :flex-wrap "wrap"
                     :min-height "32px"}}
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
          "Clear All"))))
