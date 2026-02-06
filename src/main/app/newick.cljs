(ns app.newick
  "Parses Newick-format phylogenetic tree strings into nested ClojureScript maps.

  Newick format represents trees using nested parentheses with optional
  branch names and lengths. For example: `(A:0.1,B:0.2)Root:0.3;`

  The main entry point is [[newick->map]], which returns a recursive map
  of `{:name :branch-length :children}`."
  (:require [clojure.string :as str]))

(defn- tokenize
  "Splits a Newick-format string into a sequence of structural tokens.

  Tokens include parentheses `(` `)`, commas `,`, colons `:`,
  taxon labels, and branch length numbers. Trailing semicolons
  are stripped before tokenization.

  Returns an empty list for nil or empty input."
  [newick-str]
  (cond (nil? newick-str)
        '()
        :else
        (-> newick-str
            (str/replace #";$" "")
            (str/split #"(?=[(),:])|(?<=[(),:])")
            (->> (map str/trim)
                 (remove empty?)))))

(comment
  (tokenize nil)
  (tokenize "")
  (tokenize "(Dog:0.1,Cat:0.2)Mammal:0.5;")
  (tokenize "(Dog,Cat)Mammal;"))

(declare parse-node)

(defn- parse-children
  "Parses a sequence of sibling nodes from the token stream.

  Called after an opening `(` token has been consumed. Collects
  child nodes separated by commas until a closing `)` is reached.

  Returns a tuple of `[children remaining-tokens]`."
  [tokens]
  (loop [current-tokens tokens
         children []]
    (let [[node remaining] (parse-node current-tokens)
          next-token (first remaining)]
      (cond
        (= "," next-token) (recur (rest remaining) (conj children node))
        (= ")" next-token) [(conj children node) (rest remaining)]
        :else [children remaining]))))

(defn- parse-label-and-length
  "Extracts an optional node name and branch length from the token stream.

  Handles four cases: `Name:Length`, `Name`, `:Length`, or neither.
  Branch lengths are parsed as JavaScript floats, or `nil` if missing
  or not parseable.

  Returns a tuple of `[name branch-length remaining-tokens]`."
  [tokens]
  (let [token (first tokens)]
    (if (or (nil? token) (= "," token) (= ")" token))
      [nil nil tokens]
      (let [[name-part remaining] (if (not= ":" token) [token (rest tokens)] [nil tokens])
            [len-part final-remaining] (if (= ":" (first remaining))
                                         [(second remaining) (nnext remaining)]
                                         [nil remaining])
            parsed-len (js/parseFloat len-part)
            len-part (if (NaN? parsed-len) nil parsed-len)]
        [name-part len-part final-remaining]))))

(defn- parse-node
  "Recursively parses a single node (and its subtree) from the token stream.

  An opening `(` indicates an internal node with children; otherwise
  the node is treated as a leaf. After parsing children (if any),
  extracts the node's optional name and branch length.

  Returns a tuple of `[node-map remaining-tokens]` where `node-map` has
  keys `:name` (string or nil), `:branch-length` (number or NaN), and
  `:children` (vector of child node maps)."
  [tokens]
  (let [token (first tokens)]
    (cond
      (= "(" token)
      (let [[children remaining] (parse-children (rest tokens))
            [name len next-tokens] (parse-label-and-length remaining)]
        [{:name name :branch-length len :children children} next-tokens])

      :else
      (let [[name len next-tokens] (parse-label-and-length tokens)]
        [{:name name :branch-length len :children []} next-tokens]))))

(comment
  (parse-node (tokenize "(Dog:0.1,Cat:0.2)Mammal:0.5;"))
  (parse-node (tokenize "(Dog,Cat)Mammal;"))
  )

(defn newick->map
  "Parses a Newick-format string into a nested tree map.

  Input should be a standard Newick string, optionally terminated
  by a semicolon. For example:

    \"(A:0.1,(B:0.2,C:0.3):0.4)Root:0.5;\"

  Returns a recursive map with keys:
  - `:name`          - node label (string or nil)
  - `:branch-length` - distance to parent (number or NaN)
  - `:children`      - vector of child node maps (empty for leaves)"
  [s]
  (first (parse-node (tokenize s))))

(comment
  ;; Usage Example:
  (def cat-dog-tree "(Dog:0.1,Cat:0.2)Mammal:0.5;")
  (def ab-tree "(A,B);")
  (newick->map ab-tree)
  (newick->map cat-dog-tree)

  (def abc-tree
    "(((A:1.575,
        B:1.575)
        C:5.99484,
      ((D:5.1375,
       (E:4.21625,
       (F:1.32,
       (G:0.525,
        H:0.525)
        I:0.795)
        J:2.89625)
        K:0.92125)
        L:1.5993,
      ((M:2.895,
       (N:2.11,
        O:2.11)
        P:0.785)
        Q:3.1725,
        R:6.0675)
        S:0.6693)
        T:1.50234)
        U:2.86223,
      ((V:1.58,
       (W:1.055,
        X:1.055)
        Y:0.525)
        Z:5.17966,
      (AA:4.60414,
      (AB:2.95656,
     ((AC:1.8425,
      (AD:0.525,
       AE:0.525)
       AF:1.3175)
       AG:0.99844,
     ((AH:1.1975,
      (AI:1.055,
       (AJ:0,
        AK:0)
        AL:1.055)
        AM:0.1425)
        AN:0.92281,
       (AO:1.58,
        AP:1.58)
        AQ:0.54031)
        AR:1.26094)
        AS:1.11406)
        AT:1.64758)
        AU:2.15552)
        AV:4.32559)
        AW:10.4109")

  (tap> (newick->map abc-tree)))
