# Development Guide

## Dev Workflow

### Starting the dev server

```bash
npm run dev
# Equivalent to: npx shadow-cljs watch app
```

This starts shadow-cljs in watch mode, serving the app at `http://localhost:8080` with hot module reloading. Code changes in `src/main/` are automatically compiled and the `re-render` function is called to update the UI.

### REPL-driven development

From a Clojure nREPL (e.g. via your editor), connect to the shadow-cljs REPL:

```clojure
(require '[user :refer [cljs-repl]])
(cljs-repl)        ;; connects to the :app build
(cljs-repl :test)  ;; or connect to the :test build
```

This gives you a live ClojureScript REPL connected to the running browser session.

### Running tests during development

```bash
npm run test:watch
```

This compiles and runs tests on every save. The `:test` build uses `:node-test` target, so tests run in Node.js without a browser.

## Working with Specs

The `app.specs` namespace defines specs for all core data structures and key functions. To use specs in the REPL:

```clojure
(require '[cljs.spec.alpha :as s])
(require '[app.specs])

;; Validate a tree node
(s/valid? :app.specs/tree-node
  {:name "A" :branch-length 0.1 :children []})
;; => true

;; Explain why something doesn't conform
(s/explain :app.specs/tree-node
  {:name "A"})
;; => val: {:name "A"} fails spec: :app.specs/tree-node
;;    predicate: (contains? % :branch-length)

;; Check a parsed metadata structure
(s/valid? :app.specs/parsed-metadata
  {:headers [{:key :Name :label "Name" :width 120}]
   :data [{:Name "Alice"}]})
;; => true
```

## Adding a New Metadata Column Feature

1. If you need new column behavior, add it to `csv/parse-metadata` in `app.csv`
2. Update the `::metadata-header` spec in `app.specs` if the shape changes
3. The `PhylogeneticTree` component in `app.core` handles rendering via `MetadataColumn`

## Modifying Tree Layout

The layout algorithm is a two-pass process:

1. `assign-y-coords` — depth-first traversal assigning sequential y values to leaves
2. `assign-x-coords` — depth-first traversal accumulating branch lengths as x values

To change spacing, modify the `LAYOUT` constant in `app.core`. To change the algorithm itself, modify the `assign-*` functions and update corresponding tests in `app.core-test`.
