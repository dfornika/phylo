(ns app.generators
  "Shared test.check generators for generative testing.

  Provides generators for Newick tree structures, metadata rows,
  CSV content, date strings, and other domain types used across
  the Phylo test suite."
  (:require [clojure.test.check.generators :as gen]))

;; ===== Leaf Names =====

(def gen-leaf-name
  "Generator for simple leaf/taxon names (1-8 alphanumeric characters)."
  (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 1 8)))

;; ===== Tree Maps =====

(defn gen-tree-map
  "Generator for parsed tree maps matching the `::tree-node` spec.

  Produces trees with the given maximum depth and branching factor.
  Leaf nodes have nil children vectors (empty `[]`), optional names,
  and optional branch lengths.

  Options:
  - `max-depth`       - maximum nesting depth (default 4)
  - `max-branching`   - maximum children per internal node (default 3)"
  ([] (gen-tree-map 4 3))
  ([max-depth max-branching]
   (if (<= max-depth 0)
     ;; Leaf node
     (gen/let [name gen-leaf-name
               len  (gen/double* {:min 0.0001 :max 10.0 :NaN? false :infinite? false})]
       {:name name :branch-length len :children []})
     ;; Internal or leaf (biased toward leaves at shallow depths)
     (gen/frequency
      [[1 ;; internal node
        (gen/let [n-children (gen/choose 2 max-branching)
                  children   (gen/vector (gen-tree-map (dec max-depth) max-branching)
                                         2 max-branching)
                  name       (gen/one-of [(gen/return nil) gen-leaf-name])
                  len        (gen/one-of [(gen/return nil)
                                         (gen/double* {:min 0.0001 :max 10.0
                                                       :NaN? false :infinite? false})])]
          {:name name :branch-length len :children children})]
       [2 ;; leaf node
        (gen/let [name gen-leaf-name
                  len  (gen/double* {:min 0.0001 :max 10.0 :NaN? false :infinite? false})]
          {:name name :branch-length len :children []})]]))))

;; ===== Newick Strings =====

(defn tree-map->newick
  "Serializes a generated tree map to a Newick string (no trailing semicolon)."
  [{:keys [name branch-length children]}]
  (let [children-str (when (seq children)
                       (str "(" (clojure.string/join "," (map tree-map->newick children)) ")"))
        name-str (or name "")
        len-str  (when (some? branch-length) (str ":" branch-length))]
    (str children-str name-str len-str)))

(def gen-newick
  "Generator for syntactically valid Newick strings.
  Produces trees with 2-8 tips and branch lengths."
  (gen/fmap #(str (tree-map->newick %) ";") (gen-tree-map 3 3)))

;; ===== Metadata =====

(def gen-metadata-row
  "Generator for a single metadata row (map of keyword->string)."
  (gen/let [id    gen-leaf-name
            val1  (gen/fmap str gen/nat)
            val2  gen-leaf-name]
    {:id id :col-a val1 :col-b val2}))

;; ===== CSV/TSV =====

(def gen-csv-row
  "Generator for a CSV row as a vector of string cells."
  (gen/vector (gen/fmap str (gen/one-of [gen/nat gen-leaf-name])) 2 5))

;; ===== Dates =====

(def gen-date-str
  "Generator for date strings in YYYY-MM-DD format."
  (gen/let [year  (gen/choose 1900 2100)
            month (gen/choose 1 12)
            day   (gen/choose 1 28)] ;; 28 avoids invalid month-end dates
    (str year "-"
         (.padStart (str month) 2 "0") "-"
         (.padStart (str day) 2 "0"))))

;; ===== Scale =====

(def gen-pos-number
  "Generator for positive numbers suitable for scale calculations."
  (gen/double* {:min 0.001 :max 1000.0 :NaN? false :infinite? false}))
