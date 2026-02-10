(ns app.import.arborview
  "Parsers for ArborView standalone HTML exports.

  Extracts TREE (Newick) and DATA (TSV) strings embedded as JS literals."
  (:require [clojure.string :as str]))

(defn- parse-hex
  "Parses a hex string into an integer, returning nil on failure."
  [s]
  (when (and s (not (str/blank? s)))
    (let [n (js/parseInt s 16)]
      (when-not (js/isNaN n)
        n))))

(defn- unescape-js-string
  "Unescapes common JS string escapes from a string literal body."
  [s]
  (when s
    (let [length (.-length s)]
      (loop [idx 0
             out (transient [])]
        (if (>= idx length)
          (apply str (persistent! out))
          (let [ch (.charAt s idx)]
            (if (= ch "\\")
              (if (< (inc idx) length)
                (let [esc (.charAt s (inc idx))]
                  (case esc
                    "n" (recur (+ idx 2) (conj! out "\n"))
                    "r" (recur (+ idx 2) (conj! out "\r"))
                    "t" (recur (+ idx 2) (conj! out "\t"))
                    "\\" (recur (+ idx 2) (conj! out "\\"))
                    "\"" (recur (+ idx 2) (conj! out "\""))
                    "'" (recur (+ idx 2) (conj! out "'"))
                    "u" (let [end (+ idx 6)
                              hex (when (<= end length) (subs s (+ idx 2) end))
                              code (parse-hex hex)]
                          (if code
                            (recur end (conj! out (js/String.fromCharCode code)))
                            (recur (+ idx 2) (conj! out esc))))
                    "x" (let [end (+ idx 4)
                              hex (when (<= end length) (subs s (+ idx 2) end))
                              code (parse-hex hex)]
                          (if code
                            (recur end (conj! out (js/String.fromCharCode code)))
                            (recur (+ idx 2) (conj! out esc))))
                    (recur (+ idx 2) (conj! out esc))))
                (recur (inc idx) (conj! out ch)))
              (recur (inc idx) (conj! out ch)))))))))

(defn- extract-js-string
  "Extracts a JS string literal assigned to a given var name.

  Supports const/let/var and both single and double quoted strings."
  [html name]
  (let [pattern (re-pattern
                 (str "(?s)(?:const|let|var)\\s+" name
                      "\\s*=\\s*(\"|')((?:\\\\.|(?!\\1).)*?)\\1"))]
    (when-let [[_ _ body] (re-find pattern html)]
      (unescape-js-string body))))

(defn parse-arborview-html
  "Parses ArborView HTML and returns extracted TREE and DATA strings.

  Returns a map with keys:
  - :newick-str   - Newick string when present
  - :metadata-raw - TSV string when present"
  [html]
  (when (and html (not (str/blank? html)))
    (let [tree (extract-js-string html "TREE")
          data (extract-js-string html "DATA")]
      (cond-> {}
        (some? tree) (assoc :newick-str tree)
        (some? data) (assoc :metadata-raw data)))))
