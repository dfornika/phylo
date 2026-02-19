(ns app.components.toolbar
  "Toolbar and control panel components.

  Contains [[Toolbar]] (file loaders, sliders, toggles). Reads shared
  state from React context via [[app.state/use-app-state]]."
  (:require [uix.core :as uix :refer [defui $]]
            [app.csv :as csv]
            [clojure.string :as str]
            [app.import.arborview :as arbor]
            [app.import.nextstrain :as nextstrain]
            [app.state :as state]
            [app.export.html :as export-html]
            [app.export.svg :as export-svg]
            [app.layout :refer [LAYOUT]]
            [app.io :as io]
            [app.newick :as newick]
            [app.tree :as tree]))

;; ===== Style constants =====

(def ^:private toolbar-font
  "System sans-serif font stack for the toolbar."
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif")

(def ^:private navy "#003366")

(def ^:private label-style
  {:font-family toolbar-font
   :font-size "13px"
   :font-weight 600
   :color navy
   :white-space "nowrap"})

(def ^:private section-style
  "Shared style for visually grouped toolbar sections."
  {:display "flex"
   :align-items "center"
   :gap "8px"
   :padding "4px 8px"
   :background "#f0f2f5"
   :border-radius "6px"})

(def ^:private group-style
  {:display "flex"
   :align-items "center"
   :gap "10px"
   :padding "4px 6px"
   :border "1px solid #dee2e6"
   :border-radius "8px"
   :background "#ffffff"})

;; ===== Main Toolbar =====

(defui Toolbar
  "Renders the control panel with file loaders, layout sliders, and toggles.

  Reads all state from [[app.state/app-context]] via [[app.state/use-app-state]],
  so this component requires no props."
  [_props]
  (let [{:keys [newick-str set-newick-str!
                parsed-tree set-parsed-tree!
                positioned-tree set-positoned-tree!
                x-mult set-x-mult!
                y-mult set-y-mult!
                col-spacing set-col-spacing!
                tree-metadata-gap-px set-tree-metadata-gap-px!
                show-internal-markers set-show-internal-markers!
                show-scale-gridlines set-show-scale-gridlines!
                show-distance-from-origin set-show-distance-from-origin!
                scale-origin set-scale-origin!
                show-pixel-grid set-show-pixel-grid!  ;; Temporarily disabled pixel grid, these aren't needed but will be if pixel grid is re-enabled.
                set-metadata-rows! set-active-cols!
                set-selected-ids! set-highlights!
                active-internal-node-id set-active-internal-node-id!]} (state/use-app-state)]
    ($ :div {:style {:padding "6px 8px"
                     :background "#ffffff"
                     :border-bottom "2px solid #e2e6ea"
                     :display "flex"
                     :gap "12px"
                     :align-items "center"
                     :flex-wrap "wrap"
                     :font-family toolbar-font
                     :color navy}}

       ;; ── Import ──
       ($ :div {:style group-style}
          ($ :div {:style (merge section-style {:gap "12px"})}
             ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
                ($ :label {:style label-style} "Tree")
                ($ :input {:type "file"
                           :accept ".nwk,.newick,.tree,.txt"
                           :style {:font-family toolbar-font :font-size "12px" :color navy}
                           :on-change #(io/read-file! % (fn [content]
                                                          (set-newick-str! (.trim content))
                                                          (set-parsed-tree! nil)
                                                          (set-metadata-rows! [])
                                                          (set-active-cols! [])
                                                          (set-selected-ids! #{})
                                                          (set-highlights! {})))}))
             ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
                ($ :label {:style label-style} "Metadata")
                ($ :input {:type "file"
                           :accept ".csv,.tsv,.txt"
                           :style {:font-family toolbar-font :font-size "12px" :color navy}
                           :on-change #(io/read-file! % (fn [content]
                                                          (let [{:keys [headers data]} (csv/parse-metadata content (:default-col-width LAYOUT))]
                                                            (set-metadata-rows! data)
                                                            (set-active-cols! headers))))})))
          ($ :div {:style section-style}
             ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
                ($ :label {:style label-style} "ArborView HTML")
                ($ :input {:type "file"
                           :accept ".html,.htm"
                           :style {:font-family toolbar-font :font-size "12px" :color navy}
                           :on-change #(io/read-file! % (fn [content]
                                                          (let [{:keys [newick-str metadata-raw]} (arbor/parse-arborview-html content)]
                                                            (when newick-str
                                                              (set-newick-str! (str/trim newick-str)))
                                                            (set-parsed-tree! nil)
                                                            (if metadata-raw
                                                              (let [{:keys [headers data]} (csv/parse-metadata metadata-raw (:default-col-width LAYOUT))]
                                                                (set-metadata-rows! data)
                                                                (set-active-cols! headers))
                                                              (do
                                                                (set-metadata-rows! [])
                                                                (set-active-cols! [])))
                                                            (set-selected-ids! #{})
                                                            (set-highlights! {}))))}))
             ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
                ($ :label {:style label-style} "Nextstrain JSON")
                ($ :input {:type "file"
                           :accept ".json"
                           :style {:font-family toolbar-font :font-size "12px" :color navy}
                           :on-change #(io/read-file! % (fn [content]
                                                          (let [{:keys [newick-str parsed-tree]} (nextstrain/parse-nextstrain-json content)]
                                                            (when newick-str
                                                              (set-newick-str! (str/trim newick-str))
                                                              (set-parsed-tree! parsed-tree)
                                                              (set-metadata-rows! [])
                                                              (set-active-cols! [])
                                                              (set-selected-ids! #{})
                                                              (set-highlights! {})))))}))))

       ;; ── Controls ──
       ($ :div {:style group-style}
          ($ :div {:style section-style}
             ($ :label {:style label-style} "Width")
             ($ :input {:type "range"
                        :min 0.05 :max 1.5 :step 0.01
                        :value x-mult
                        :style {:width "80px" :accent-color navy}
                        :on-change #(set-x-mult! (js/parseFloat (.. % -target -value)))}))
          ($ :div {:style section-style}
             ($ :label {:style label-style} "Height")
             ($ :input {:type "range"
                        :min 10 :max 100
                        :value y-mult
                        :style {:width "80px" :accent-color navy}
                        :on-change #(set-y-mult! (js/parseInt (.. % -target -value) 10))}))
          ($ :div {:style section-style}
             ($ :label {:style label-style} "Tree-Metadata Gap")
             ($ :input {:type "range"
                        :min -50 :max 200 :step 1
                        :value tree-metadata-gap-px
                        :style {:width "80px" :accent-color navy}
                        :on-change #(set-tree-metadata-gap-px! (js/parseInt (.. % -target -value) 10))}))
          ($ :div {:style section-style}
             ($ :label {:style label-style} "Metadata Columns")
             ($ :input {:type "range"
                        :min 0 :max 50 :step 1
                        :value col-spacing
                        :style {:width "64px" :accent-color navy}
                        :on-change #(set-col-spacing! (js/parseInt (.. % -target -value) 10))}))
          ($ :div {:style (merge section-style {:gap "10px"})}
             ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
                ($ :input {:type "checkbox"
                           :checked show-internal-markers
                           :style {:accent-color navy}
                           :on-change #(set-show-internal-markers! (not show-internal-markers))})
                "Internal Nodes")
             ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
                ($ :input {:type "checkbox"
                           :checked show-scale-gridlines
                           :style {:accent-color navy}
                           :on-change #(set-show-scale-gridlines! (not show-scale-gridlines))})
                "Scale Lines")
             ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
                ($ :input {:type "checkbox"
                           :checked show-distance-from-origin
                           :style {:accent-color navy}
                           :on-change #(set-show-distance-from-origin! (not show-distance-from-origin))})
                "Distance from Origin")
             ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
                ($ :label {:style label-style} "Scale Origin")
                ($ :select {:value (name scale-origin)
                            :style {:font-family toolbar-font
                                    :font-size "12px"
                                    :color navy
                                    :border "1px solid #cfd6de"
                                    :border-radius "6px"
                                    :padding "2px 6px"
                                    :background "#ffffff"}
                            :on-change #(set-scale-origin! (keyword (.. % -target -value)))}
                   ($ :option {:value "tips"} "Tips")
                   ($ :option {:value "root"} "Root")))
             ($ :button {:disabled (nil? active-internal-node-id)
                         :on-click (fn [_]
                                     (js/console.log "Re-rooting tree...")
                                     (js/console.log "Active internal node:" active-internal-node-id)
                                     (js/console.log "Positioned tree:" positioned-tree)
                                     (when (and active-internal-node-id positioned-tree)
                                       ;; Strip coordinates to get un-positioned tree
                                       (let [strip-coords (fn strip [n]
                                                            (-> n
                                                                (dissoc :x :y :leaf-names)
                                                                (update :children #(mapv strip %))))
                                             unpositioned (strip-coords positioned-tree)
                                             _ (js/console.log "Unpositioned tree:" (clj->js unpositioned))
                                             rerooted (tree/reroot-tree unpositioned active-internal-node-id)]
                                         (when rerooted
                                           (set-parsed-tree! rerooted)
                                           (set-newick-str! (newick/map->newick rerooted))
                                           (set-active-internal-node-id! nil)))))
                         :style {}}
                "Re-root Tree")
             ($ :button {:disabled (not (or newick-str parsed-tree))
                         :on-click (fn [_]
                                     (let [current-tree (or parsed-tree
                                                            (when newick-str
                                                              (newick/newick->map newick-str)))
                                           ladderized (tree/ladderize current-tree :ascending)]
                                       (set-parsed-tree! ladderized)
                                       (set-newick-str! (newick/map->newick ladderized))))
                         :style {}}
                "Ladderize ↓")
             ($ :button {:disabled (not (or newick-str parsed-tree))
                         :on-click (fn [_]
                                     (let [current-tree (or parsed-tree
                                                            (when newick-str
                                                              (newick/newick->map newick-str)))
                                           ladderized (tree/ladderize current-tree :descending)]
                                       (set-parsed-tree! ladderized)
                                       (set-newick-str! (newick/map->newick ladderized))))
                         :style {}}
                "Ladderize ↑"))
             ;; Temporarily disabled this toggle for the PixelGrid.
             ;; only intended as dev-time troubleshooting tool.
          #_($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
               ($ :input {:type "checkbox"
                          :checked show-pixel-grid
                          :style {:accent-color navy}
                          :on-change #(set-show-pixel-grid! (not show-pixel-grid))})
               "Pixel Grid"))

;; ── Export ──
       ($ :div {:style (merge group-style {:margin-left "auto"})}
          ($ :button {:on-click (fn [_] (export-svg/export-svg!))
                      :style {:font-family toolbar-font
                              :font-size "13px"
                              :font-weight 600
                              :color navy
                              :padding "6px 12px"
                              :cursor "pointer"
                              :background "#f0f2f5"
                              :border (str "1px solid " navy)
                              :border-radius "6px"
                              :transition "background 0.15s"}}
             "\u21E9 SVG")
          ($ :button {:on-click (fn [_] (export-html/export-html!))
                      :style {:font-family toolbar-font
                              :font-size "13px"
                              :font-weight 600
                              :color navy
                              :padding "6px 12px"
                              :cursor "pointer"
                              :background "#f0f2f5"
                              :border (str "1px solid " navy)
                              :border-radius "6px"
                              :transition "background 0.15s"}}
             "\u21E9 HTML")))))
