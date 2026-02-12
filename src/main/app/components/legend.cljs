(ns app.components.legend
  "Floating SVG legend overlay for color mappings."
  (:require [uix.core :as uix :refer [defui $]]
            [clojure.string :as str]))

(def ^:private legend-width 200)
(def ^:private header-height 20)
(def ^:private row-height 16)
(def ^:private section-gap 8)
(def ^:private padding 8)
(def ^:private swatch-size 10)

(defn- clamp
  [value min-v max-v]
  (-> value (max min-v) (min max-v)))

(defn- client->svg
  [^js svg client-x client-y]
  (when-let [^js ctm (.getScreenCTM svg)]
    (let [^js pt (.matrixTransform
                  (js/DOMPoint. client-x client-y)
                  (.inverse ctm))]
      [(.-x pt) (.-y pt)])))

(defn- legend-height
  [sections collapsed?]
  (if collapsed?
    (+ header-height 2)
    (let [entry-count (reduce + 0 (map (comp count :entries) sections))
          section-count (count sections)
          titles-height (* section-count 12)
          entries-height (* entry-count row-height)
          gaps (if (pos? section-count) (* (dec section-count) section-gap) 0)
          body (+ titles-height entries-height gaps)]
      (+ header-height padding body padding 2))))

(defui FloatingLegend
  [{:keys [svg-ref svg-width svg-height
           title sections
           pos set-pos!
           collapsed? set-collapsed!
           labels set-labels!]}]
  (let [{:keys [x y]} pos
        pos-x (or x 0)
        pos-y (or y 0)
        legend-h (legend-height sections collapsed?)
        max-x (max 0 (- (or svg-width 0) legend-width))
        max-y (max 0 (- (or svg-height 0) legend-h))
        [editing-color set-editing-color!] (uix/use-state nil)
        [editing-text set-editing-text!] (uix/use-state "")
        start-drag
        (fn [^js e]
          (.stopPropagation e)
          (.preventDefault e)
          (when-let [^js svg @svg-ref]
            (when-let [[sx sy] (client->svg svg (.-clientX e) (.-clientY e))]
              (let [offset-x (- sx pos-x)
                    offset-y (- sy pos-y)
                    on-move (fn [^js me]
                              (when-let [[mx my] (client->svg svg (.-clientX me) (.-clientY me))]
                                (let [next-x (clamp (- mx offset-x) 0 max-x)
                                      next-y (clamp (- my offset-y) 0 max-y)]
                                  (set-pos! {:x next-x :y next-y}))))
                    on-up (fn on-up-fn [_]
                            (.removeEventListener js/document "mousemove" on-move)
                            (.removeEventListener js/document "mouseup" on-up-fn))]
                (.addEventListener js/document "mousemove" on-move)
                (.addEventListener js/document "mouseup" on-up)))))
        set-label!
        (fn [color value]
          (let [next (str/trim (or value ""))
                base-labels (or labels {})]
            (set-labels!
             (if (str/blank? next)
               (dissoc base-labels color)
               (assoc base-labels color next)))))
        commit-edit
        (fn []
          (when editing-color
            (set-label! editing-color editing-text)
            (set-editing-color! nil)
            (set-editing-text! "")))]

    (uix/use-effect
     (fn []
       (when (or (nil? x) (nil? y))
         (let [default-x (max 0 (- (or svg-width 0) legend-width padding))
               default-y (max 0 padding)]
           (set-pos! {:x default-x :y default-y}))))
     [x y svg-width svg-height set-pos!])

    ($ :g {:transform (str "translate(" pos-x "," pos-y ")")
           :style {:pointer-events "all"}
           :on-mouse-down (fn [^js e] (.stopPropagation e))}
       ;; Outer frame
       ($ :rect {:x 0 :y 0
                 :width legend-width
                 :height legend-h
                 :fill "#f7f7f7"
                 :stroke "#000"
                 :stroke-width 1})

       ;; Header (System 8 style)
       ($ :g {:on-mouse-down start-drag}
          ($ :rect {:x 0 :y 0
                    :width legend-width
                    :height header-height
                    :fill "#d6d6d6"
                    :stroke "#000"
                    :stroke-width 1})
          ($ :line {:x1 1 :y1 1 :x2 (dec legend-width) :y2 1
                    :stroke "#ffffff" :stroke-width 1})
          ($ :line {:x1 1 :y1 (dec header-height) :x2 (dec legend-width) :y2 (dec header-height)
                    :stroke "#7f7f7f" :stroke-width 1})
          ($ :text {:x padding :y 14
                    :style {:font-family "Geneva, Chicago, 'Helvetica Neue', Arial, sans-serif"
                            :font-size "11px"
                            :font-weight 600
                            :fill "#111"}}
             (or title "Legend"))
          ;; Collapse button
          ($ :g {:transform (str "translate(" (- legend-width 20) ",4)")
                 :on-mouse-down (fn [^js e]
                                  (.stopPropagation e)
                                  (.preventDefault e)
                                  (set-collapsed! (not collapsed?)))}
             ($ :rect {:x 0 :y 0 :width 12 :height 12
                       :fill "#efefef" :stroke "#000" :stroke-width 1})
             ($ :line {:x1 3 :y1 6 :x2 9 :y2 6 :stroke "#000" :stroke-width 1})
             (when collapsed?
               ($ :line {:x1 6 :y1 3 :x2 6 :y2 9 :stroke "#000" :stroke-width 1}))))

       ;; Body
       (when (and (not collapsed?) (seq sections))
         (let [body-x padding
               body-y (+ header-height padding)]
           ($ :g {:transform (str "translate(" body-x "," body-y ")")}
              (loop [section-idx 0
                     y-offset 0
                     section-items sections
                     nodes []]
                (if-let [{:keys [title entries]} (first section-items)]
                  (let [title-node
                        ($ :text {:key (str "section-title-" section-idx)
                                  :x 0 :y (+ y-offset 10)
                                  :style {:font-family "Geneva, Chicago, 'Helvetica Neue', Arial, sans-serif"
                                          :font-size "10px"
                                          :font-weight 600
                                          :fill "#333"}}
                           title)
                        entry-nodes
                        (map-indexed
                         (fn [idx {:keys [id color label editable? placeholder?]}]
                           (let [row-y (+ y-offset 16 (* idx row-height))
                                 label-x (+ swatch-size 8)
                                 label-style {:font-family "Geneva, Chicago, 'Helvetica Neue', Arial, sans-serif"
                                              :font-size "10px"
                                              :fill (if placeholder? "#777" "#111")}
                                 editing? (= editing-color color)]
                             ($ :g {:key (str "entry-" id)
                                    :transform (str "translate(0," row-y ")")}
                                ($ :rect {:x 0 :y 2 :width swatch-size :height swatch-size
                                          :fill color :stroke "#111" :stroke-width 0.5})
                                (if editing?
                                  ($ :foreignObject {:x label-x :y -1 :width (- legend-width 40) :height 18}
                                     ($ :input {:type "text"
                                                :value editing-text
                                                :auto-focus true
                                                :on-change (fn [e] (set-editing-text! (.. e -target -value)))
                                                :on-blur (fn [_] (commit-edit))
                                                :on-key-down (fn [e]
                                                               (when (= "Enter" (.-key e))
                                                                 (commit-edit))
                                                               (when (= "Escape" (.-key e))
                                                                 (set-editing-color! nil)
                                                                 (set-editing-text! "")))
                                                :style {:font-size "10px"
                                                        :height "16px"
                                                        :width "100%"
                                                        :border "1px solid #999"
                                                        :padding "0 3px"
                                                        :font-family "Geneva, Chicago, 'Helvetica Neue', Arial, sans-serif"}}))
                                  ($ :text {:x label-x :y 10
                                            :style (merge label-style (when editable? {:cursor "text"}))
                                            :on-mouse-down (fn [^js e]
                                                             (.stopPropagation e)
                                                             (.preventDefault e)
                                                             (when editable?
                                                               (set-editing-color! color)
                                                               (set-editing-text! (or (get labels color) ""))))}
                                     label)))))
                         entries)
                        next-nodes (into nodes (cons title-node entry-nodes))]
                    (recur (inc section-idx)
                           (+ y-offset 12 (* (count entries) row-height) section-gap)
                           (rest section-items)
                           next-nodes))
                  ($ :g nodes)))))))))
