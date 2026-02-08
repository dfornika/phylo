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
   :padding "6px 12px"
   :background "#f0f2f5"
   :border-radius "6px"})

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
    ($ :div {:style {:padding "10px 16px"
                     :background "#ffffff"
                     :border-bottom "2px solid #e2e6ea"
                     :display "flex"
                     :gap "12px"
                     :align-items "center"
                     :flex-wrap "wrap"
                     :font-family toolbar-font
                     :color navy}}

       ;; ── File loaders ──
       ($ :div {:style (merge section-style {:gap "16px"})}
          ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
             ($ :label {:style label-style} "Tree")
             ($ :input {:type "file"
                        :accept ".nwk,.newick,.tree,.txt"
                        :style {:font-family toolbar-font :font-size "12px" :color navy}
                        :on-change #(read-file! % (fn [content]
                                                    (set-newick-str! (.trim content))))}))
          ($ :div {:style {:display "flex" :align-items "center" :gap "6px"}}
             ($ :label {:style label-style} "Metadata")
             ($ :input {:type "file"
                        :accept ".csv,.tsv,.txt"
                        :style {:font-family toolbar-font :font-size "12px" :color navy}
                        :on-change #(read-file! % (fn [content]
                                                    (let [{:keys [headers data]} (csv/parse-metadata content (:default-col-width LAYOUT))]
                                                      (set-metadata-rows! data)
                                                      (set-active-cols! headers))))})))

       ;; ── Sliders ──
       ($ :div {:style section-style}
          ($ :label {:style label-style} "Tree Width")
          ($ :input {:type "range"
                     :min 0.05 :max 1.5 :step 0.01
                     :value x-mult
                     :style {:width "90px" :accent-color navy}
                     :on-change #(set-x-mult! (js/parseFloat (.. % -target -value)))}))

       ($ :div {:style section-style}
          ($ :label {:style label-style} "Tree Height")
          ($ :input {:type "range"
                     :min 10 :max 100
                     :value y-mult
                     :style {:width "90px" :accent-color navy}
                     :on-change #(set-y-mult! (js/parseInt (.. % -target -value) 10))}))

       ($ :div {:style section-style}
          ($ :label {:style label-style} "Metadata Column Gap")
          ($ :input {:type "range"
                     :min 0 :max 50 :step 1
                     :value col-spacing
                     :style {:width "70px" :accent-color navy}
                     :on-change #(set-col-spacing! (js/parseInt (.. % -target -value) 10))}))

       ;; ── Toggles ──
       ($ :div {:style (merge section-style {:gap "14px"})}
          ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
             ($ :input {:type "checkbox"
                        :checked show-internal-markers
                        :style {:accent-color navy}
                        :on-change #(set-show-internal-markers! (not show-internal-markers))})
             "Internal Node Markers")
          ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
             ($ :input {:type "checkbox"
                        :checked show-scale-gridlines
                        :style {:accent-color navy}
                        :on-change #(set-show-scale-gridlines! (not show-scale-gridlines))})
             "Scale Gridlines")
          ($ :label {:style (merge label-style {:display "flex" :align-items "center" :gap "4px" :cursor "pointer"})}
             ($ :input {:type "checkbox"
                        :checked show-pixel-grid
                        :style {:accent-color navy}
                        :on-change #(set-show-pixel-grid! (not show-pixel-grid))})
             "Pixel Grid"))
       ;; ── Export ──
       ($ :button {:on-click (fn [_] (export-svg!))
                   :style {:font-family toolbar-font
                           :font-size "13px"
                           :font-weight 600
                           :color navy
                           :padding "6px 14px"
                           :cursor "pointer"
                           :background "#f0f2f5"
                           :border (str "1px solid " navy)
                           :border-radius "6px"
                           :transition "background 0.15s"
                           :margin-left "auto"}}
          "\u21E9 Export SVG"))))
