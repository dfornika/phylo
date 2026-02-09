(ns app.components.toolbar
  "Toolbar and control panel components.

  Contains [[Toolbar]] (file loaders, sliders, toggles). Reads shared
  state from React context via [[app.state/use-app-state]]."
  (:require [uix.core :as uix :refer [defui $]]
            [clojure.string :as str]
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
  ([blob filename]
   (save-blob! blob filename [{:description "SVG Image"
                               :accept {"image/svg+xml" [".svg"]}}]))
  ([blob filename types]
   (if (exists? js/window.showSaveFilePicker)
     (-> (.showSaveFilePicker js/window
                              (clj->js {:suggestedName filename
                                        :types types}))
         (.then (fn [handle]
                  (-> (.createWritable handle)
                      (.then (fn [writable]
                               (-> (.write writable blob)
                                   (.then #(.close writable))))))))
         (.catch (fn [err]
                   (when-not (= (.-name err) "AbortError")
                     (js/console.error "File System Access API failed:" err)
                     (fallback-download! blob filename)))))
     (fallback-download! blob filename))))

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

(defn- fetch-text
  "Fetches a URL and resolves to its text content."
  [url]
  (-> (js/fetch url)
      (.then (fn [resp]
               (if (.-ok resp)
                 (.text resp)
                 (js/Promise.reject
                  (js/Error. (str "Failed to fetch " url ": " (.-status resp)))))))))

(defn- fetch-blob
  "Fetches a URL and resolves to its Blob content."
  [url]
  (-> (js/fetch url)
      (.then (fn [resp]
               (if (.-ok resp)
                 (.blob resp)
                 (js/Promise.reject
                  (js/Error. (str "Failed to fetch " url ": " (.-status resp)))))))))

(defn- blob->data-url
  "Converts a Blob into a data: URL string."
  [blob]
  (js/Promise.
   (fn [resolve reject]
     (let [reader (js/FileReader.)]
       (set! (.-onload reader)
             (fn [e]
               (resolve (-> e .-target .-result))))
       (set! (.-onerror reader)
             (fn [_]
               (reject (js/Error. "Failed to read blob"))))
       (.readAsDataURL reader blob)))))

(defn- inlineable-src?
  "Returns true when a src/href is safe to fetch and inline."
  [src]
  (and (string? src)
       (not (str/blank? src))
       (not (str/starts-with? src "blob:"))
       (not (str/starts-with? src "data:"))))

(defn- collect-script-srcs
  "Returns ordered script src URLs from the current document."
  []
  (->> (.querySelectorAll js/document "script[src]")
       array-seq
       (map #(.getAttribute % "src"))
       (filter inlineable-src?)
       distinct
       vec))

(defn- collect-stylesheet-hrefs
  "Returns ordered stylesheet href URLs from the current document."
  []
  (->> (.querySelectorAll js/document "link[rel=\"stylesheet\"][href]")
       array-seq
       (map #(.getAttribute % "href"))
       (filter inlineable-src?)
       distinct
       vec))

(defn- escape-script-content
  "Escapes script content to avoid prematurely closing a tag."
  [content]
  (-> content
      (str/replace "</script" "<\\/script")))

(defn- fetch-texts
  "Fetches a list of URLs and resolves to a vector of {:url :text} maps.
  Failed fetches are logged and skipped."
  [urls]
  (let [promises (map (fn [url]
                        (-> (fetch-text url)
                            (.then (fn [text] {:url url :text text}))
                            (.catch (fn [err]
                                      (js/console.error "Failed to inline" url err)
                                      nil))))
                      urls)]
    (-> (js/Promise.all (clj->js promises))
        (.then (fn [results]
                 (->> results (filter some?) vec))))))

(defn- fetch-asset-map
  "Fetches assets and resolves to a map of {path data-url}."
  [paths]
  (let [promises (map (fn [path]
                        (-> (fetch-blob path)
                            (.then (fn [blob]
                                     (-> (blob->data-url blob)
                                         (.then (fn [data-url]
                                                  {:path path :data-url data-url})))))
                            (.catch (fn [err]
                                      (js/console.error "Failed to inline asset" path err)
                                      nil))))
                      paths)]
    (-> (js/Promise.all (clj->js promises))
        (.then (fn [results]
                 (reduce (fn [acc {:keys [path data-url]}]
                           (assoc acc path data-url))
                         {}
                         (filter some? results)))))))

(defn- build-export-html
  "Builds a standalone HTML document from inlined assets and state."
  [{:keys [styles scripts asset-map state-edn]}]
  (let [style-tags (apply str
                          (map (fn [{:keys [url text]}]
                                 (str "<style data-href=\"" url "\">\n"
                                      text
                                      "\n</style>\n"))
                               styles))
        script-tags (apply str
                           (map (fn [{:keys [url text]}]
                                  (str "<script data-src=\"" url "\">\n"
                                       (escape-script-content text)
                                       "\n</script>\n"))
                                scripts))
        assets-json (.stringify js/JSON (clj->js asset-map))
        assets-script (str "<script>window.__PHYLO_ASSET_MAP__ = " assets-json ";</script>\n")
        state-script (str "<script id=\"phylo-export-state\" type=\"application/edn\">"
                          (escape-script-content state-edn)
                          "</script>\n")]
    (str "<!DOCTYPE html>\n"
         "<html>\n"
         "  <head>\n"
         "    <meta charset=\"UTF-8\">\n"
         "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
         style-tags
         "  </head>\n"
         "  <body>\n"
         "    <div id=\"app\"></div>\n"
         assets-script
         state-script
         script-tags
         "  </body>\n"
         "</html>\n")))

(defn export-html!
  "Exports a standalone HTML file with inlined assets and embedded state." 
  []
  (let [state-edn (pr-str (state/export-state))
        script-srcs (collect-script-srcs)
        style-hrefs (collect-stylesheet-hrefs)
        asset-paths ["images/logo.svg"]]
    (-> (js/Promise.all
         (clj->js [(fetch-texts style-hrefs)
                   (fetch-texts script-srcs)
                   (fetch-asset-map asset-paths)]))
        (.then (fn [results]
                 (let [[styles scripts asset-map] (js->clj results)
                       html (build-export-html {:styles styles
                                                :scripts scripts
                                                :asset-map asset-map
                                                :state-edn state-edn})
                       blob (js/Blob. #js [html] #js {:type "text/html;charset=utf-8"})]
                   (save-blob! blob
                               "phylo-viewer.html"
                               [{:description "HTML File"
                                 :accept {"text/html" [".html"]}}]))))
        (.catch (fn [err]
                  (js/console.error "Failed to export HTML" err))))))

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
       ($ :div {:style {:display "flex"
                        :gap "8px"
                        :margin-left "auto"}}
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
                              :transition "background 0.15s"}}
             "\u21E9 Export SVG")
          ($ :button {:on-click (fn [_] (export-html!))
                      :style {:font-family toolbar-font
                              :font-size "13px"
                              :font-weight 600
                              :color navy
                              :padding "6px 14px"
                              :cursor "pointer"
                              :background "#f0f2f5"
                              :border (str "1px solid " navy)
                              :border-radius "6px"
                              :transition "background 0.15s"}}
             "\u21E9 Export HTML")))))
