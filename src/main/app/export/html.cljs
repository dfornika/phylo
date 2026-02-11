(ns app.export.html
  "Standalone HTML export pipeline.

  Collects runtime scripts, styles, assets, and embeds serialized
  application state into a self-contained HTML file."
  (:require [clojure.string :as str]
            [app.state :as state]))

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

(defn save-blob!
  "Triggers a browser file save for the given Blob.

  Attempts the File System Access API (`showSaveFilePicker`) first,
  which opens a native \"Save as...\" dialog. Falls back to a
  programmatic `<a download>` click for browsers that do not
  support it (Firefox, Safari)."
  ([blob filename]
   (save-blob! blob filename [{:description "HTML File"
                               :accept {"text/html" [".html"]}}]))
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

(defn inlineable-src?
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

(defn- collect-inline-script-tags
  "Returns ordered inline script blocks tagged with data-src."
  []
  (->> (.querySelectorAll js/document "script[data-src]")
       array-seq
       (map (fn [el]
              {:url (.getAttribute el "data-src")
               :text (.-textContent el)}))
       (filter (fn [{:keys [url text]}]
                 (and (inlineable-src? url) (string? text))))
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

(defn- collect-inline-style-tags
  "Returns ordered inline style blocks tagged with data-href."
  []
  (->> (.querySelectorAll js/document "style[data-href]")
       array-seq
       (map (fn [el]
              {:url (.getAttribute el "data-href")
               :text (.-textContent el)}))
       (filter (fn [{:keys [url text]}]
                 (and (inlineable-src? url) (string? text))))
       vec))

(defn escape-script-content
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

(defn build-export-html
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
        inline-styles (collect-inline-style-tags)
        inline-scripts (collect-inline-script-tags)
        inline-style-urls (into #{} (map :url inline-styles))
        inline-script-urls (into #{} (map :url inline-scripts))
        style-hrefs (->> (collect-stylesheet-hrefs)
                         (remove inline-style-urls)
                         vec)
        script-srcs (->> (collect-script-srcs)
                         (remove inline-script-urls)
                         vec)
        asset-paths ["images/logo.svg"]]
    (-> (js/Promise.all
         (clj->js [(fetch-texts style-hrefs)
                   (fetch-texts script-srcs)
                   (fetch-asset-map asset-paths)]))
        (.then (fn [results]
                 (let [[styles scripts asset-map] (js->clj results)
                       all-styles (vec (concat inline-styles styles))
                       all-scripts (vec (concat inline-scripts scripts))
                       html (build-export-html {:styles all-styles
                                                :scripts all-scripts
                                                :asset-map asset-map
                                                :state-edn state-edn})
                       blob (js/Blob. #js [html] #js {:type "text/html;charset=utf-8"})]
                   (save-blob! blob "phylo-viewer.html"))))
        (.catch (fn [err]
                  (js/console.error "Failed to export HTML" err))))))
