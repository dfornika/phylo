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


## Component Prop Validation (Dev Only)

Component prop specs are colocated with their components, but the validation logic lives in `app.specs`:

- `app.specs.clj` defines `defui-with-spec`, a macro that wraps `defui` and calls `validate-spec!` in dev.
- `app.specs.cljs` defines the specs and the `validate-spec!` helper.

Example usage:

```clojure
(defui-with-spec TreeNode
  [{:spec :app.specs/tree-node-props :props props
    :opts {:check-unexpected-keys? true}}]
  ($ TreeNode* props))
```

Notes:
- The macro keeps UIx prop handling intact, so props are still converted as usual.
- Validation runs only under `goog.DEBUG`, so release builds are unaffected.

## Adding a New Metadata Column Feature

1. If you need new column behavior, add it to `csv/parse-metadata` in `app.csv`
2. Update the `::metadata-header` spec in `app.specs` if the shape changes
3. The `MetadataColumn` component in `app.components.metadata` handles rendering; `MetadataTable` manages column layout


## TSX Component Development

Pure rendering components are being extracted as TypeScript/TSX alongside the existing UIx implementations. This enables future portability to other React projects and Storybook-based component testing. **The UIx components remain the canonical implementations for now.**

### Building TSX components

```bash
npm run tsx:build    # One-shot compile
npm run tsx:watch    # Continuous compile during dev
```

TSX sources live in `src/tsx/components/` and compile to `src/gen/components/` (gitignored). The compiled JS is on the shadow-cljs classpath and can be imported from ClojureScript.

### Adding a new TSX component

1. Create `src/tsx/components/MyComponent.tsx`
2. Import shared types from `./types` and sibling components as needed
3. Define a props interface — all rendering parameters must come via props (no implicit layout/state dependencies)
4. Export a named function component
5. Run `npm run tsx:build` to compile
6. Keep the corresponding UIx component in its `app.components.*` namespace in sync

### Design guidelines

- TSX components should be **pure functions of their props** — the ClojureScript layer remains the single source of truth for layout constants and application state.
- Shared TypeScript interfaces (e.g. `PositionedNode`) live in `src/tsx/components/types.ts` and mirror the `clojure.spec` definitions in `app.specs`.
- When passing complex data structures (e.g. tree nodes) from CLJS to TSX, the CLJS layer must convert from ClojureScript maps to plain JS objects (e.g. via `clj->js`).
- UIx's `$` macro automatically converts kebab-case keys to camelCase for non-UIx (JS) components, so `:parent-x` becomes `parentX`.

## Modifying Tree Layout

The layout algorithm in `app.tree/prepare-tree` is a multi-step pipeline:

1. `assign-y-coords` — depth-first traversal assigning sequential y values to leaves
2. `assign-x-coords` — depth-first traversal accumulating branch lengths as x values
3. `assign-node-ids` — depth-first traversal assigning unique `:id` integers for stable React keys
4. `assign-leaf-names` — bottom-up traversal precomputing a `:leaf-names` set (descendant leaf names) on every node

To change spacing, modify the `LAYOUT` constant in `app.layout`. To change the algorithm itself, modify the `assign-*` functions in `app.tree` and update corresponding tests in `app.tree-test`.

Scale tick calculations (shared by the scale bar, sticky header, and gridlines) live in `app.scale`. Browser file I/O helpers (`save-blob!`, `read-file!`) live in `app.io`. Small shared utilities (`client->svg`, `clamp`) live in `app.util`.
