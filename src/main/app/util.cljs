(ns app.util
  "Shared utility functions used across the app.

  Contains general-purpose helpers that don't belong to any specific
  domain namespace (e.g. SVG coordinate conversion, numeric clamping).")

(defn clamp
  "Clamps `value` to the inclusive range [`min-v`, `max-v`]."
  [value min-v max-v]
  (-> value (max min-v) (min max-v)))

(defn client->svg
  "Convert client (screen) coordinates to SVG user-space coordinates.
  Returns [svg-x svg-y] or nil if the SVG's CTM is unavailable."
  [^js svg client-x client-y]
  (when-let [^js ctm (.getScreenCTM svg)]
    (let [^js pt (.matrixTransform
                  (js/DOMPoint. client-x client-y)
                  (.inverse ctm))]
      [(.-x pt) (.-y pt)])))
