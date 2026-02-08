(ns app.components.toolbar
  "Toolbar and control panel components.

  Contains [[Toolbar]] (file loaders, sliders, toggles). Reads shared
  state from React context via [[app.state/use-app-state]]."
  (:require [uix.core :as uix :refer [defui $]]
            [app.csv :as csv]
            [app.state :as state]
            [app.layout :refer [LAYOUT]]))

;; ===== File I/O =====

(defn read-file!
  "Reads a text file selected by the user via an `<input type=\"file\">` element.

  Extracts the first file from the JS event's target, reads it as text
  using a `FileReader`, and calls `on-read-fn` with the string content
  when loading completes. This is a side-effecting function (note the `!`)."
  [js-event on-read-fn]
  (when-let [file (-> js-event .-target .-files (aget 0))]
    (let [reader (js/FileReader.)]
      (set! (.-onload reader)
            (fn [e] (on-read-fn (-> e .-target .-result))))
      (.readAsText reader file))))

(defn- fallback-download!
  "Downloads the blob using the fallback `<a download>` method.

  Creates a temporary anchor element with an object URL, clicks it,
  then cleans up asynchronously to avoid racing the download."
  [blob filename]
  (let [url (.createObjectURL js/URL blob)
        a   (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild (.-body js/document) a)
    (.click a)
    (js/setTimeout
     (fn []
       (.removeChild (.-body js/document) a)
       (.revokeObjectURL js/URL url))
     0)))

(defn- save-blob!
  "Triggers a browser file save for the given Blob.

  Attempts the File System Access API (`showSaveFilePicker`) first,
  which opens a native \"Save as...\" dialog. Falls back to a
  programmatic `<a download>` click for browsers that do not
  support it (Firefox, Safari)."
  [blob filename]
  (if (exists? js/window.showSaveFilePicker)
    (-> (.showSaveFilePicker js/window
                             (clj->js {:suggestedName filename
                                       :types [{:description "SVG Image"
                                                :accept {"image/svg+xml" [".svg"]}}]}))
        (.then (fn [handle]
                 (-> (.createWritable handle)
                     (.then (fn [writable]
                              (-> (.write writable blob)
                                  (.then #(.close writable))))))))
        (.catch (fn [err]
                  (when-not (= (.-name err) "AbortError")
                    (js/console.error "File System Access API failed:" err)
                    (fallback-download! blob filename)))))
    (fallback-download! blob filename)))

(defn export-svg!
  "Exports the phylogenetic tree SVG to a file.

  Grabs the live `<svg>` DOM node by its id, clones it, adds the
  `xmlns` attribute required for standalone SVG files, serializes
  it to XML text, and triggers a save dialog."
  []
  (when-let [svg-el (js/document.getElementById "phylo-svg")]
    (let [clone      (.cloneNode svg-el true)
          _          (.setAttribute clone "xmlns" "http://www.w3.org/2000/svg")
          serializer (js/XMLSerializer.)
          svg-str    (.serializeToString serializer clone)
          blob       (js/Blob. #js [svg-str] #js {:type "image/svg+xml;charset=utf-8"})]
      (save-blob! blob "phylo-tree.svg"))))

;; ===== Main Toolbar =====

(defui Toolbar
  "Renders the control panel with file loaders, layout sliders, and toggles.

  Reads all state from [[app.state/app-context]] via [[app.state/use-app-state]],
  so this component requires no props."
  [_props]
  (let [{:keys [x-mult set-x-mult!
                y-mult set-y-mult!
                col-spacing set-col-spacing!
                show-internal-markers set-show-internal-markers!
                show-scale-gridlines set-show-scale-gridlines!
                show-pixel-grid set-show-pixel-grid!
                set-newick-str!
                set-metadata-rows! set-active-cols!]} (state/use-app-state)]
    ($ :div {:style {:padding "12px"
                     :background "#f8f9fa"
                     :border-bottom "1px solid #ddd"
                     :display "flex"
                     :gap (str (:toolbar-gap LAYOUT) "px")
                     :align-items "center"
                     :flex-wrap "wrap"}}
       ($ :div {:style {:display "flex" :gap "10px" :padding "10px" :background "#eee" :border-radius "4px"}}
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Load Tree (Newick): ")
             ($ :input {:type "file"
                        :accept ".nwk,.newick,.tree,.txt"
                        :on-change #(read-file! % (fn [content]
                                                    (set-newick-str! (.trim content))))}))
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Load Metadata (CSV/TSV): ")
             ($ :input {:type "file"
                        :accept ".csv,.tsv,.txt"
                        :on-change #(read-file! % (fn [content]
                                                    (let [{:keys [headers data]} (csv/parse-metadata content (:default-col-width LAYOUT))]
                                                      (set-metadata-rows! data)
                                                      (set-active-cols! headers))))})))
       ($ :button {:on-click (fn [_] (export-svg!))
                   :style {:font-weight "bold"
                           :padding "8px 16px"
                           :cursor "pointer"
                           :background "#fff"
                           :border "1px solid #ccc"
                           :border-radius "4px"}}
          "\u21E9 Export SVG")
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Tree Width: ")
          ($ :input {:type "range"
                     :min 0.05
                     :max 1.5
                     :step 0.01
                     :value x-mult
                     :on-change #(set-x-mult! (js/parseFloat (.. % -target -value)))}))
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Tree Height: ")
          ($ :input {:type "range"
                     :min 10
                     :max 100
                     :value y-mult
                     :on-change #(set-y-mult! (js/parseInt (.. % -target -value) 10))}))
       ($ :div
          ($ :label {:style {:font-weight "bold"}} "Column Spacing: ")
          ($ :input {:type "range"
                     :min 0
                     :max 50
                     :step 1
                     :value col-spacing
                     :on-change #(set-col-spacing! (js/parseInt (.. % -target -value) 10))}))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-internal-markers-checkbox"
                     :checked show-internal-markers
                     :on-change #(set-show-internal-markers! (not show-internal-markers))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-internal-markers-checkbox"}} "Show internal node markers"))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-scale-gridlines-checkbox"
                     :checked show-scale-gridlines
                     :on-change #(set-show-scale-gridlines! (not show-scale-gridlines))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-scale-gridlines-checkbox"}} "Show scale gridlines"))
       ($ :div {:style {:display "flex" :align-items "center" :gap "5px"}}
          ($ :input {:type "checkbox"
                     :id "show-pixel-grid-checkbox"
                     :checked show-pixel-grid
                     :on-change #(set-show-pixel-grid! (not show-pixel-grid))})
          ($ :label {:style {:font-weight "bold"
                             :htmlFor "show-pixel-grid-checkbox"}} "Show pixel grid")))))
