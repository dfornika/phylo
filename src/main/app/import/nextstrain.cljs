(ns app.import.nextstrain
  "Parser for Nextstrain JSON exports.

  Extracts the 'tree' object and converts it into a Newick string
  that can be fed into the existing pipeline."
  (:require [clojure.string :as str]))

(defn- parse-div
  "Returns a numeric divergence value when present, else nil."
  [node]
  (let [div (get-in node [:node_attrs :div])]
    (cond
      (number? div) div
      (map? div) (let [v (:value div)]
                   (when (number? v) v))
      :else nil)))

(defn- node-name
  "Returns the node name as a string, or nil when missing."
  [node]
  (let [name (:name node)]
    (when (string? name) name)))

(defn- normalize-div
  "Normalizes divergence by falling back to parent-div when missing."
  [node parent-div]
  (let [div (parse-div node)]
    (cond
      (number? div) div
      (number? parent-div) parent-div
      :else 0)))

(defn- to-tree-map
  "Converts a Nextstrain node into the internal tree-node map."
  [node parent-div is-root?]
  (let [div (normalize-div node parent-div)
        branch-length (if is-root?
                        nil
                        (if (number? parent-div)
                          (- div parent-div)
                          0))
        children (mapv #(to-tree-map % div false) (:children node))]
    {:name (node-name node)
     :branch-length branch-length
     :children children}))

(defn- escape-name
  "Escapes a node name for Newick when it contains special characters."
  [name]
  (when (some? name)
    (if (re-find #"[\s\(\)\:,;]" name)
      (str "'" (str/replace name #"'" "''") "'")
      name)))

(defn- format-branch-length
  "Formats branch length for Newick output."
  [len]
  (when (number? len)
    (str ":" (str len))))

(defn- tree->newick
  "Serializes a tree map into a Newick string."
  [node]
  (let [children (:children node)
        children-str (when (seq children)
                       (str "(" (str/join "," (map tree->newick children)) ")"))
        name-str (escape-name (:name node))
        len-str (format-branch-length (:branch-length node))]
    (str (or children-str "")
         (or name-str "")
         (or len-str ""))))

(defn parse-nextstrain-json
  "Parses Nextstrain JSON and returns a map with :newick-str when successful.

  Expected shape: top-level object with a 'tree' field. Each node should
  have optional :name, :node_attrs {:div ...}, and :children."
  [json-str]
  (when (and json-str (not (str/blank? json-str)))
    (try
      (let [parsed (js->clj (js/JSON.parse json-str) :keywordize-keys true)
            tree (:tree parsed)]
        (when (map? tree)
          {:newick-str (str (tree->newick (to-tree-map tree nil true)) ";")}))
      (catch :default err
        (js/console.warn "Failed to parse Nextstrain JSON:" err)
        {:error :invalid-json}))))
