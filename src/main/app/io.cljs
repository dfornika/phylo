(ns app.io
  "Browser file I/O utilities.

  Contains side-effecting helpers for reading files from `<input>` elements
  and triggering downloads via the File System Access API or `<a download>`
  fallback. Used by toolbar (file loading) and export modules (saving).")

;; ===== Download / Save =====

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

;; ===== File Reading =====

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
