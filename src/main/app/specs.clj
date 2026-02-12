(ns app.specs
  "Macros for component spec validation."
  (:require [uix.core :as uix]))

(defmacro defui-with-spec
  [name [spec-map] & body]
  (let [props-sym (:props spec-map)
        spec (:spec spec-map)
        opts-form (or (:opts spec-map) {})
        argv [props-sym]]
    `(uix/defui ~name ~argv
       (when ^boolean goog.DEBUG
         (app.specs/validate-spec! ~props-sym ~spec "component props" ~opts-form))
       ~@body)))
