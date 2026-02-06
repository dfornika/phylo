(ns app.core
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.newick :as newick]
            [app.csv :as csv]))

(defn get-max-x
  "Get the maximum x-axis (horizontal) position of a node in a tree"
  [node]
  (if (empty? (:children node))
    (:x node)
    (apply max (:x node) (map get-max-x (:children node)))))


(defn count-tips
  "Count the number of tips (leaf nodes) in a tree"
  [node]
  (if (empty? (:children node))
    1
    (reduce + (map count-tips (:children node)))))


(defn assign-y-coords
  ""
  [node next-y]
  (if (empty? (:children node))
    [(assoc node :y @next-y) (swap! next-y inc)]
    (let [processed-children (mapv #(first (assign-y-coords % next-y)) (:children node))
          avg-y (/ (+ (:y (first processed-children)) 
                      (:y (last processed-children))) 2)]
      [(assoc node :children processed-children :y avg-y) @next-y])))


(defn assign-x-coords
  ""
  ([node]
   (assign-x-coords node 0 true)) ;; Helper for root call
  ([node current-x is-root?]
   (let [;; If it's the root, we ignore its own length to keep it at x=0
         len (if is-root? 0 (or (:branch-length node) 0))
         new-x (+ current-x len)]
     (assoc node :x new-x 
            :children (mapv #(assign-x-coords % new-x false) 
                            (:children node))))))


(defn read-file!
  ""
  [js-event on-read-fn]
  (let [file (-> js-event .-target .-files (aget 0))
        reader (js/FileReader.)]
    (set! (.-onload reader) 
          (fn [e] (on-read-fn (-> e .-target .-result))))
    (.readAsText reader file)))

(defn parse-metadata [content]
  (let [lines (-> content str/trim str/split-lines)
        first-line (first lines)
        delimiter (if (str/includes? first-line "\t") #"\t" #",")
        ;; Extract raw header strings
        raw-headers (map #(-> % str/trim (str/replace #"^\"|\"$" "")) 
                         (str/split first-line delimiter))
        ;; Convert to keywords for data access, but keep strings for labels
        header-configs (map (fn [h] 
                              {:key (keyword h) 
                               :label h 
                               :width 120}) ; Default width
                            raw-headers)
        data-rows (keep (fn [line]
                          (when (not (str/blank? line))
                            (let [values (map #(-> % str/trim (str/replace #"^\"|\"$" "")) 
                                              (str/split line delimiter))]
                              (zipmap (map :key header-configs) values))))
                        (rest lines))]
    {:headers header-configs
     :data data-rows}))


(defn create-metadata-index
  [rows id-key]
  (into {} (map (fn [row] [(get row id-key) row]) rows)))




(defui MetadataHeader [{:keys [columns start-offset]}]
  ($ :div {:style {:position "sticky"
                   :top 0
                   :z-index 10
                   :background "#f8f9fa"
                   :border-bottom "2px solid #dee2e6"
                   :height "36px"
                   :display "flex"
                   :align-items "center"
                   ;; This 40px padding matches the SVG's translate(40, ...)
                   :padding-left "40px" 
                   :font-family "sans-serif"
                   :font-size "12px"
                   :font-weight "bold"}}
     ;; This spacer pushes the headers to exactly where the metadata columns start
     ;; We subtract the 40px we already added in padding to keep the math pure
     ($ :div {:style {:width (str (- start-offset 80) "px") :flex-shrink 0}} 
        "Phylogeny")
     
     (for [{:keys [key label width]} columns]
       ($ :div {:key key :style {:width (str width "px") :flex-shrink 0}}
          label))))



(defui MetadataColumn [{:keys [tips x-offset y-scale column-key]}]
  ($ :g
     (for [tip tips]
       ($ :text {:key (str column-key "-" (:name tip))
                 :x x-offset
                 ;; We add a vertical offset (e.g., 40) to match the Tree's start
                 :y (+ (* (:y tip) y-scale) 40) 
                 :dominant-baseline "central" ;; <--- Crucial for vertical centering
                 :style {:font-family "monospace"
                         :font-size "12px"}}
       
          ;; Assuming metadata is stored in the node or a map
          (get-in tip [:metadata column-key] "N/A")))))

(defui Branch [{:keys [x y parent-x parent-y line-color line-width]}]
  ($ :g
     ;; Horizontal branch
     ($ :line {:x1 parent-x :y1 y :x2 x :y2 y :stroke line-color :stroke-width line-width})
     ;; Vertical connector to siblings
     ($ :line {:x1 parent-x :y1 parent-y :x2 parent-x :y2 y :stroke line-color :stroke-width line-width})))


(defui TreeNode [{:keys [node parent-x parent-y x-scale y-scale]}]
  (let [scaled-x (* (:x node) x-scale)
        scaled-y (* (:y node) y-scale)
        p-x (* parent-x x-scale)
        p-y (* parent-y y-scale)
        line-width 0.5
        line-color "#000"]
    ($ :g
       ($ Branch {:x scaled-x :y scaled-y :parent-x p-x :parent-y p-y :line-color line-color :line-width line-width})
       
       ;; If it's a leaf node (tip)
       (when (empty? (:children node))
         ($ :g
            ;; The Tip Label
            ($ :text {:x (+ scaled-x 8) 
                      :y scaled-y 
                      :dominant-baseline "central"
                      :style {:font-family "monospace" :font-size "12px" :font-weight "bold"}} 
               (:name node))))
       
       ;; Recursion for children
       (for [child (:children node)]
         ($ TreeNode {:key (:name child)
                      :node child 
                      :parent-x (:x node) 
                      :parent-y (:y node) 
                      :x-scale x-scale 
                      :y-scale y-scale})))))


(defn calculate-scale-unit
  ""
  [max-x]
  (let [log10 (js/Math.log10 max-x)
        magnitude (js/Math.pow 10 (js/Math.floor log10))
        ratio (/ max-x magnitude)]
    (cond
      (< ratio 2) (* magnitude 0.1)
      (< ratio 5) (* magnitude 0.5)
      :else magnitude)))


(defn get-ticks
  [max-x unit]
  (take-while #(<= % max-x)
              (iterate #(+ % unit) 0)))


(defui ScaleHeader [{:keys [max-x x-scale start-offset]}]
  (let [unit (calculate-scale-unit (/ max-x 5))
        ticks (get-ticks max-x unit)]
    ($ :div {:style {:position "sticky"
                     :top "36px" ;; Sits just below the Metadata labels
                     :z-index 9
                     :background "rgba(255,255,255,0.9)"
                     :border-bottom "1px solid #ccc"
                     :height "40px"}}
       ($ :svg {:width "100%" :height "100%"}
          ($ :g {:transform "translate(40, 30)"}
             (for [t ticks]
               ($ :g {:key t :transform (str "translate(" (* t x-scale) ", 0)")}
                  ($ :line {:y1 -5 :y2 0 :stroke "#666" :stroke-width 1})
                  ($ :text {:y -10 :text-anchor "middle" 
                            :style {:font-family "monospace" :font-size "10px"}}
                     (.toFixed t 3)))))))))


(defn get-leaves
  ""
  [n]
  (if (empty? (:children n))
    [n]
    (mapcat get-leaves (:children n))))


(defui PhylogeneticTree [{:keys [newick-str width-px component-height-px]}]
  (let [[metadata-rows set-metadata!] (uix/use-state [])
        [x-mult set-x-mult!] (uix/use-state 0.5) ;; 50% width by default
        [y-mult set-y-mult!] (uix/use-state 30)
        [metadata-rows set-rows!] (uix/use-state [])
        [active-cols set-cols!] (uix/use-state [])

        ;; 1. Process Tree and Merge Metadata
        { :keys [tree tips max-depth] } (uix/use-memo
                                         (fn []
                                           (let [root (-> (newick/newick->map newick-str)
                                                          (assign-y-coords (atom 0))
                                                          first
                                                          assign-x-coords)
                                                 leaves (get-leaves root)
                                                 ;; Create a lookup map indexed by the first column (usually ID)
                                                 ;; or a specific key like :Sample_ID if known.
                                                 id-key (-> active-cols first :key)
                                                 metadata-index (into {} (map (fn [r] [(get r id-key) r]) metadata-rows))
                                                 ;; Inject metadata into tips
                                                 enriched-leaves (mapv #(assoc % :metadata (get metadata-index (:name %))) 
                                                                       leaves)]
                                             {:tree root 
                                              :tips enriched-leaves 
                                              :max-depth (get-max-x root)}))
                                         [newick-str metadata-rows active-cols])
        
        ;; --- DYNAMIC LAYOUT MATH ---
        ;; We set a "working area" for the tree. 
        ;; x-mult now determines how much of the width the tree consumes.
        label-buffer 150
        current-x-scale (* (/ (- width-px 400) max-depth) x-mult)

        ;; The point where metadata starts is now relative to the tree's end
        tree-end-x (+ (* max-depth current-x-scale) label-buffer)
        metadata-start-x (+ tree-end-x 40)]

    ($ :div {:style {:display "flex" :flex-direction "column" :height (str component-height-px "px")}}

       ;; --- TOOLBAR ---
       ;; TODO: Factor this out into a separate component
       ($ :div {:style {:padding "12px" :background "#f8f9fa" :border-bottom "1px solid #ddd" :display "flex" :gap "20px"}}
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Tree Width: ")
             ($ :input {:type "range" :min 0.05 :max 1.5 :step 0.01 :value x-mult 
                        :on-change #(set-x-mult! (js/parseFloat (.. % -target -value)))}))
          ($ :div
             ($ :label {:style {:font-weight "bold"}} "Vertical Spacing: ")
             ($ :input {:type "range" :min 10 :max 100 :value y-mult 
                        :on-change #(set-y-mult! (js/parseInt (.. % -target -value)))}))
          ($ :div {:style {:display "flex" :gap "10px" :padding "10px" :background "#eee"}}
             ($ :div
                ($ :label "Load Metadata (CSV/TSV): ")
                ($ :input {:type "file" 
                           :accept ".csv,.tsv,.txt"
                           :on-change #(read-file! % (fn [content]
                                                       (let [{:keys [headers data]} (parse-metadata content)]
                                                         (set-rows! data)
                                                         (set-cols! headers)
                                                         (js/console.log "Loaded rows:" (count data)))))}))))

       ;; --- SCROLLABLE VIEWPORT ---
       ($ :div {:style {:flex "1" :overflow "auto" :position "relative" :border-bottom "2px solid #dee2e6"}}
          (when (seq active-cols)
            ($ MetadataHeader {:columns active-cols :start-offset metadata-start-x}))

          ($ :svg {:width (+ metadata-start-x (reduce + (map :width active-cols)) 100)
                   :height (+ (* (count tips) y-mult) 100)}
             ;; Scale lines.
             ;; TODO: Factor out into separate component, add controls to toggle on/off
             (let [unit (calculate-scale-unit (/ max-depth 5))
                   ticks (get-ticks max-depth unit)
                   tree-height (* (count tips) y-mult)]
               ($ :g {:transform "translate(40, 40)"}
                  ($ :g
                     (for [t ticks]
                       ($ :line {:key (str "grid-" t)
                                 :x1 (* t current-x-scale) :y1 0
                                 :x2 (* t current-x-scale) :y2 tree-height
                                 :stroke "#eee"
                                 :stroke-dasharray "4 4"
                                 :stroke-width 1})))
                  ($ TreeNode {:node tree :parent-x 0 :parent-y (:y tree) 
                               :x-scale current-x-scale :y-scale y-mult})))

                ;; Render columns
                (let [offsets (reductions (fn [acc col] (+ acc (:width col))) 
                                          metadata-start-x
                                          active-cols)]
                  (map-indexed 
                    (fn [idx col]
                      ($ MetadataColumn {:key (str "col-" (:key col))
                                         :tips tips 
                                         :x-offset (- (nth offsets idx) 40) 
                                         :y-scale y-mult 
                                         :column-key (:key col)}))
                    active-cols)))

          ))))



(def abc-tree
    "(((A:1.575,
B:1.575
)C:5.99484,
((D:5.1375,
(E:4.21625,
(F:1.32,
(G:0.525,
H:0.525
)I:0.795
)J:2.89625
)K:0.92125
)L:1.5993,
((M:2.895,
(N:2.11,
O:2.11
)P:0.785
)Q:3.1725,
R:6.0675
)S:0.6693
)T:1.50234
)U:2.86223,
((V:1.58,
(W:1.055,
X:1.055
)Y:0.525
)Z:5.17966,
(AA:4.60414,
(AB:2.95656,
((AC:1.8425,
(AD:0.525,
AE:0.525
)AF:1.3175
)AG:0.99844,
((AH:1.1975,
(AI:1.055,
(AJ:0,
AK:0
)AL:1.055
)AM:0.1425
)AN:0.92281,
(AO:1.58,
AP:1.58
)AQ:0.54031
)AR:1.26094
)AS:1.11406
)AT:1.64758
)AU:2.15552
)AV:4.32559
)AW:10.4109")


(defui app []
  (let [[state set-state!] (uix/use-state {})]
    ($ PhylogeneticTree {:newick-str abc-tree
                         :metadata-map {}
                         :width-px 1200
                         :component-height-px 800})))

(defonce root (uix.dom/create-root (js/document.getElementById "app")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))

(defn ^:dev/after-load re-render
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code.
  ;; This function is called implicitly by its annotation.
  (render))

(comment

  
  
  )
