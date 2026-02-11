(ns app.components.resizable-panel
  "A bottom-anchored panel with a draggable top edge for resizing.

  Wraps arbitrary children in a container whose height is controlled
  by dragging a handle bar. The panel can be collapsed down to its
  configured `:min-height` (which may be set to 0) and expanded back."
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [defui $]]
            [app.specs :as specs])
  (:require-macros [app.specs :refer [defui-with-spec]]))

(def ^:private handle-height
  "Height of the drag handle bar in pixels."
  6)

(s/def :app.specs/resizable-panel-props
  (s/keys :req-un [:app.specs/initial-height
                   :app.specs/min-height
                   :app.specs/max-height]
          :opt-un [:app.specs/height ::on-height-change]))

(defui ResizablePanel*
  "A panel with a draggable top-edge resize handle.

  The panel maintains its own height in local state. Dragging the
  handle bar up or down adjusts the panel height. The height is
  clamped between `min-height` and `max-height`.

  During drag, only local state is updated for smooth visual feedback.
  The `on-height-change` callback is fired only when the drag ends
  (on mouseup), preventing expensive re-renders of parent components.

  Props:
  - `:height`         - controlled height in pixels (optional)
  - `:initial-height` - starting height in pixels (default 250)
  - `:min-height`     - minimum panel height (default 100)
  - `:max-height`     - maximum panel height (default 600)
  - `:on-height-change` - callback fired on drag end with the final height
  - `:children`       - child elements to render inside the panel"
  [{:keys [height initial-height min-height max-height on-height-change children]
    :or {initial-height 250 min-height 100 max-height 800}}]
  (let [[panel-height set-panel-height!] (uix/use-state (or height initial-height))
        dragging-ref (uix/use-ref false)
        start-y-ref (uix/use-ref 0)
        start-h-ref (uix/use-ref 0)
        current-h-ref (uix/use-ref panel-height)]

    ;; Sync controlled height when provided
    (uix/use-effect
     (fn []
       (when (some? height)
         (let [clamped-height (-> height
                                  (max min-height)
                                  (min max-height))]
           (set-panel-height! clamped-height)))
       js/undefined)
     [height min-height max-height])

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
                           ;; Update local state immediately for smooth visual feedback
                           (set-panel-height! new-h)
                           ;; Also update ref so mouseup has the latest value
                           (reset! current-h-ref new-h))))
             on-up (fn [_e]
                     (when @dragging-ref
                       ;; Only commit to global state when drag ends
                       (when on-height-change
                         (on-height-change @current-h-ref)))
                     (reset! dragging-ref false))]
         (.addEventListener js/document "mousemove" on-move)
         (.addEventListener js/document "mouseup" on-up)
         (fn []
           (.removeEventListener js/document "mousemove" on-move)
           (.removeEventListener js/document "mouseup" on-up))))
     [on-height-change min-height max-height])

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


(defui-with-spec ResizablePanel
  [{:spec :app.specs/resizable-panel-props :props props}]
  ($ ResizablePanel* props))
#_(def ResizablePanel ResizablePanel*)