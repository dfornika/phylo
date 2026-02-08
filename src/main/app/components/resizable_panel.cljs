(ns app.components.resizable-panel
  "A bottom-anchored panel with a draggable top edge for resizing.

  Wraps arbitrary children in a container whose height is controlled
  by dragging a handle bar. The panel can be collapsed to zero height
  and expanded back."
  (:require [uix.core :as uix :refer [defui $]]))

(def ^:private handle-height
  "Height of the drag handle bar in pixels."
  6)

(defui ResizablePanel
  "A panel with a draggable top-edge resize handle.

  The panel maintains its own height in local state. Dragging the
  handle bar up or down adjusts the panel height. The height is
  clamped between `min-height` and `max-height`.

  Props:
  - `:initial-height` - starting height in pixels (default 250)
  - `:min-height`     - minimum panel height (default 100)
  - `:max-height`     - maximum panel height (default 600)
  - `:children`       - child elements to render inside the panel"
  [{:keys [initial-height min-height max-height children]
    :or {initial-height 250 min-height 100 max-height 600}}]
  (let [[panel-height set-panel-height!] (uix/use-state initial-height)
        dragging-ref (uix/use-ref false)
        start-y-ref (uix/use-ref 0)
        start-h-ref (uix/use-ref 0)]

    ;; Global mousemove/mouseup listeners for drag
    (uix/use-effect
     (fn []
       (let [on-move (fn [e]
                       (when @dragging-ref
                         (.preventDefault e)
                         (let [dy (- @start-y-ref (.-clientY e))
                               new-h (-> (+ @start-h-ref dy)
                                         (max min-height)
                                         (min max-height))]
                           (set-panel-height! new-h))))
             on-up (fn [_e]
                     (reset! dragging-ref false))]
         (.addEventListener js/document "mousemove" on-move)
         (.addEventListener js/document "mouseup" on-up)
         (fn []
           (.removeEventListener js/document "mousemove" on-move)
           (.removeEventListener js/document "mouseup" on-up))))
     [min-height max-height])

    ($ :div {:style {:height (str panel-height "px")
                     :display "flex"
                     :flex-direction "column"
                     :flex-shrink 0}}

       ;; Drag handle
       ($ :div {:style {:height (str handle-height "px")
                        :cursor "ns-resize"
                        :background "#dee2e6"
                        :border-top "1px solid #ccc"
                        :display "flex"
                        :align-items "center"
                        :justify-content "center"
                        :user-select "none"
                        :flex-shrink 0}
                :on-mouse-down (fn [e]
                                 (.preventDefault e)
                                 (reset! dragging-ref true)
                                 (reset! start-y-ref (.-clientY e))
                                 (reset! start-h-ref panel-height))}
          ;; Visual grip indicator
          ($ :div {:style {:width "40px"
                           :height "2px"
                           :background "#999"
                           :border-radius "1px"}}))

       ;; Panel content
       ($ :div {:style {:flex "1"
                        :overflow "hidden"}}
          children))))
