(ns app.generators
  "Shared test.check generators for generative testing.

  Where possible generators are derived from specs via `s/gen`,
  keeping them in sync with the spec definitions. Hand-written
  generators remain for Newick serialization, CSV rows, dates,
  and other domain types where spec-derived generation is not
  practical."
  (:require [clojure.test.check.generators :as gen]
            [cljs.spec.alpha :as s]
            [app.specs :as specs]))

;; ===== Leaf Names =====

(def gen-leaf-name
  "Generator for simple leaf/taxon names (1-8 alphanumeric characters)."
  (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 1 8)))

;; ===== Tree Maps (derived from spec) =====

(defn gen-tree-map
  "Generator for parsed tree maps matching the `::tree-node` spec.

  Delegates to the custom generator attached to `::specs/tree-node`
  when called with no arguments.  The (max-depth, max-branching)
  arity remains for tests that need explicit control over tree shape."
  ([] (s/gen ::specs/tree-node))
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
                                         n-children n-children)
                  name       (gen/one-of [(gen/return nil) gen-leaf-name])
                  len        (gen/one-of [(gen/return nil)
                                         (gen/double* {:min 0.0001 :max 10.0
                                                       :NaN? false :infinite? false})])]
          {:name name :branch-length len :children children})]
       [2 ;; leaf node
        (gen/let [name gen-leaf-name
                  len  (gen/double* {:min 0.0001 :max 10.0 :NaN? false :infinite? false})]
          {:name name :branch-length len :children []})]]))))

;; ===== Positioned Tree (derived from spec) =====

(def gen-positioned-node
  "Generator for a positioned node, derived from ::specs/positioned-node."
  (s/gen ::specs/positioned-node))

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

;; ===== Metadata (derived from spec) =====

(def gen-metadata-row
  "Generator for a single metadata row, derived from ::specs/metadata-row."
  (s/gen ::specs/metadata-row))

(def gen-metadata-header
  "Generator for a metadata header, derived from ::specs/metadata-header."
  (s/gen ::specs/metadata-header))

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
