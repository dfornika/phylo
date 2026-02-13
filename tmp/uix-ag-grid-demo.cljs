(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [kitchen-async.promise :as p]
            [lambdaisland.fetch :as fetch]
            [ag-grid-community :refer [ModuleRegistry AllCommunityModule themeBalham]]
            [ag-grid-react :as ag-grid :refer [AgGridReact AgGridColumn]]))


(.registerModules ModuleRegistry [ AllCommunityModule ])

(def app-name "UIx AG-Grid Test")
(def app-version "v0.1.0")

(def error-boundary
  (uix/create-error-boundary
   {:derive-error-state (fn [error]
                          {:error error})
    :did-catch          (fn [error info]
                          (js/console.log "Component did catch" error)
                          info)}
   (fn [[state set-state!] {:keys [children]}]
     (if-some [error (:error state)]
       ($ :<>
         ($ :p.warning "There was an error rendering!")
         ($ :pre (pr-str error)))
       children))))

(defui header
  "Header component."
  []
  ($ :header {:style {:display "grid"
                      :grid-template-columns "repeat(2, 1fr)"
                      :align-items "center"
                      :height "48px"}}
     ($ :div {:style {:display "grid"
                      :grid-template-columns "repeat(2, 1fr)"
                      :align-items "center"}}
        ($ :h1 {:style {:font-family "Arial" :color "#004a87" :margin "0px"}} app-name) ($ :p {:style {:font-family "Arial" :color "grey" :justify-self "start"}} app-version))
     ($ :div {:style {:display "grid" :align-self "center" :justify-self "end"}}
        ($ :img {:src "images/logo.svg" :height "48px"}))))


#_(defui app []
  ($ :h1 "Hello, UIx!"))

(def table-column-defs
  [{:field "number"
    :headerName "Number"}
   {:field "letter"
    :headerName "Letter"}])

(def table-row-data
  [{:number 1 :letter "A"}
   {:number 2 :letter "B"}
   {:number 3 :letter "C"}])

(defui table
  [{:keys [col-defs]}]
  (let [[row-data set-row-data!] (uix/use-state (clj->js nil))
        [loading? set-loading!] (uix/use-state true)
        [error set-error!] (uix/use-state nil)]

    (uix/use-effect
      (fn []
        (p/try
          (p/let [response (fetch/get "/data/data.json")]
            (set-row-data! (:body response))
            (set-loading! false))
          (p/catch :default e
            (set-error! (str e))
            (set-loading! false)))
        js/undefined)
      [])
    
    ($ :div {:class "ag-theme-balham"
             :style {:height "100%"}}
       (cond
         loading? ($ :div "Loading...")
         error    ($ :div (str "Error: " error))
         :else    ($ AgGridReact
                     {:rowData (clj->js row-data)
                      :columnDefs (clj->js col-defs)
                      :theme themeBalham
                      :onFirstDataRendered #(-> % .-api .sizeColumnsToFit)
                      :onSelectionChanged #()}
                     )))))


(defui app
  "Complete app component. Consists of a header and two tables."
  []
  ($ :div {:style {:display "grid"
                   :grid-template-columns "1fr"
                   :grid-gap "4px 4px"
                   :height "100%"}}
   ($ header)
   ($ :div {:style {:display "grid"
                    :grid-template-columns "1fr"
                    :grid-template-rows "1fr"
                    :gap "4px"
                    :height "800px"}}
      ($ error-boundary
         ($ table {:col-defs table-column-defs})))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root
    ($ uix/strict-mode
       ($ app))
    root))

(defn ^:export init []
  (render))
