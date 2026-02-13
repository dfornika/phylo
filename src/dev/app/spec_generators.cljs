(ns app.spec-generators
  "Registers custom generators for recursive and domain-specific specs.

  This namespace lives under `src/dev/` so that `test.check` is only
  required on the dev/test classpath — never in production builds.

  Loading this namespace (via `app.dev-preload` or a test require)
  re-defines the relevant specs with `s/with-gen`, enabling
  `s/gen`, `s/exercise`, and `stest/check` for:
  - `::specs/pos-number`
  - `::specs/tree-node`
  - `::specs/positioned-node`
  - `::specs/metadata-header`
  - `::specs/metadata-row`"
  (:require [cljs.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [app.specs :as specs]))

;; ===== Primitive Specs =====

(s/def ::specs/pos-number
  (s/with-gen
    (s/and number? pos?)
    #(gen/double* {:min 0.001 :max 1000.0 :NaN? false :infinite? false})))

;; ===== Tree Data Structures =====

(defn gen-tree-node
  "Recursive generator for tree-node maps. `depth` limits nesting."
  [depth]
  (if (<= depth 0)
    ;; Leaf
    (gen/let [nm  (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 1 6))
              len (gen/double* {:min 0.0001 :max 10.0 :NaN? false :infinite? false})]
      {:name nm :branch-length len :children []})
    ;; Internal or leaf (biased toward leaves to keep trees small)
    (gen/frequency
     [[1 (gen/let [n   (gen/choose 2 3)
                   cs  (gen/vector (gen-tree-node (dec depth)) n n)
                   nm  (gen/one-of [(gen/return nil)
                                    (gen/fmap #(apply str %)
                                             (gen/vector gen/char-alphanumeric 1 6))])
                   len (gen/one-of [(gen/return nil)
                                    (gen/double* {:min 0.0001 :max 10.0
                                                  :NaN? false :infinite? false})])]
          {:name nm :branch-length len :children cs})]
      [2 (gen/let [nm  (gen/fmap #(apply str %) (gen/vector gen/char-alphanumeric 1 6))
                   len (gen/double* {:min 0.0001 :max 10.0 :NaN? false :infinite? false})]
           {:name nm :branch-length len :children []})]])))

(s/def ::specs/tree-node
  (s/with-gen
    (s/keys :req-un [::specs/name ::specs/branch-length ::specs/children])
    #(gen-tree-node 3)))

;; Positioned node: layers x/y/id onto a generated tree-node.
(s/def ::specs/positioned-node
  (s/with-gen
    (s/keys :req-un [::specs/name ::specs/branch-length ::specs/children
                     ::specs/x ::specs/y ::specs/id]
            :opt-un [::specs/leaf-names])
    #(gen/let [node (gen-tree-node 2)
               x    (gen/double* {:min 0.0 :max 100.0 :NaN? false :infinite? false})
               y    (gen/double* {:min 0.0 :max 100.0 :NaN? false :infinite? false})
               id   gen/nat]
       (assoc node :x x :y y :id id))))

;; ===== Metadata Structures =====

(s/def ::specs/metadata-header
  (s/with-gen
    (s/keys :req-un [::specs/key ::specs/label ::specs/width]
            :opt-un [::specs/column-type ::specs/spacing])
    #(gen/let [k    (gen/fmap keyword (gen/fmap (fn [cs] (apply str cs))
                                               (gen/vector gen/char-alpha 2 8)))
               lbl  (gen/fmap (fn [cs] (apply str cs))
                              (gen/vector gen/char-alphanumeric 2 12))
               w    (gen/double* {:min 40.0 :max 300.0 :NaN? false :infinite? false})
               ct   (gen/elements [:date :numeric :string])
               sp   (gen/double* {:min 0.0 :max 20.0 :NaN? false :infinite? false})]
       {:key k :label lbl :width w :column-type ct :spacing sp})))

(s/def ::specs/metadata-row
  (s/with-gen
    (s/map-of keyword? string?)
    #(gen/let [id   (gen/fmap (fn [cs] (apply str cs))
                              (gen/vector gen/char-alphanumeric 1 8))
               val1 (gen/fmap str gen/nat)
               val2 (gen/fmap (fn [cs] (apply str cs))
                              (gen/vector gen/char-alphanumeric 1 8))]
       {:id id :col-a val1 :col-b val2})))
