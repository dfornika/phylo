# AGENTS.md

Project context for AI assistants working on Phylo.

## What Is This?

Phylo is a **ClojureScript single-page app** that renders phylogenetic trees as SVG with optional metadata overlays. It runs entirely in the browser — no backend.

## Tech Stack

| Layer          | Technology                  | Version |
|----------------|-----------------------------|---------|
| Language       | ClojureScript               | 1.12.134 |
| UI framework   | UIx (React 19 wrapper)     | 1.4.7   |
| Build tool     | shadow-cljs                 | 3.3.5   |
| Dependency mgmt| Clojure CLI (deps.edn)      | —       |
| JS runtime     | React 19 + react-refresh    | 19.2.0  |
| Test target    | Node.js (`:node-test`)      | —       |

## Quick Commands

```bash
npm run dev          # Watch mode at http://localhost:8080
npm run build        # Release build
npm run test         # One-shot test run
npm run test:watch   # Tests on every save
npm run docs         # Generate Codox docs to target/doc/
docker build -t phylo .   # Build Docker image
```

Equivalent long forms if npm isn't available:
```bash
clojure -M:dev -m shadow.cljs.devtools.cli watch app
clojure -M:dev -m shadow.cljs.devtools.cli release app
clojure -M:dev:test -m shadow.cljs.devtools.cli compile test
```

## Project Structure

```
src/main/app/
  color.cljs    — Color palette helpers, gradient/legend builders
  core.cljs     — app entry point
  csv.cljs      — CSV/TSV parsing with column metadata (pure functions)
  date.cljs     — Date parsing helpers (normalize to YYYY-MM-DD, epoch ms)
  io.cljs       — Browser file I/O utilities (save-blob!, read-file!)
  layout.cljs   — Central layout constants for the Phylo tree viewer
  newick.cljs   — recursive-descent Newick parser (pure functions)
  scale.cljs    — Scale tick calculation, origin-aware label formatting
  specs.cljs    — clojure.spec.alpha specs for all data structures & fns
  state.cljs    — defonce atoms, React context provider, use-app-state hook
  tree.cljs     — Functions for phylogenetic tree layout and analysis
  util.cljs     — Small shared helpers (client->svg, clamp)

src/main/app/components/
  grid.cljs             — AG-Grid-based metadata table component
  legend.cljs           — Color legend component for auto-coloring display
  metadata.cljs         — SVG rendering components for metadata column overlay
  resizable_panel.cljs  — A bottom-anchored panel with a draggable top edge for resizing
  selection_bar.cljs    — Selection bar component for assigning highlight colors
  toolbar.cljs          — Toolbar and control panel components (sliders, checkboxes, import/export)
  tree.cljs             — SVG rendering components for phylogenetic tree nodes and branches
  viewer.cljs           — Top-level viewer components that compose the tree visualization

src/main/app/export/
  html.cljs  — Standalone HTML export pipeline
  svg.cljs   — Standalone SVG export helper.

src/main/app/import/
  arborview.cljs   — Parsers for ArborView standalone HTML exports
  nextstrain.cljs  — Parser for Nextstrain JSON exports

src/test/app/
  arborview_import_test.cljs  — Import of trees + metadata from ArborView HTML files
  color_test.cljs             — Color palette and gradient helpers
  csv_test.cljs               — Loading data from csv/tsv (comma/tab-separated value) files
  date_test.cljs              — Date parsing and normalization
  export_html_test.cljs       — HTML export of full offline app
  generative_test.cljs        — Property-based tests (defspec) and stest/check tests
  generators.cljs             — Shared test.check generators for property-based tests
  newick_test.cljs            — Newick parser tests (tokenize, newick->map)
  nextstrain_import_test.cljs — Import of trees from Nextstrain JSON files
  scale_test.cljs             — Dynamic scale bar major/minor ticks
  specs_test.cljs             — Spec utility tests and s/exercise smoke tests
  state_test.cljs             — State export/import round-trip tests
  tree_test.cljs              — Manipulation/query of phylogenetic tree

src/dev/
  user.clj                    — dev REPL helpers (cljs-repl fn)
  app/dev_preload.cljs        — Dev-only preload: expound printer + spec instrumentation
  app/spec_generators.cljs    — Dev-only custom generators for recursive/domain specs

doc/
  01-architecture.md  — architecture overview and data flow
  02-development.md   — dev workflow, specs usage, extending the app

Dockerfile            — Multi-stage build (Clojure + nginx)
.github/workflows/
  docker.yml          — Build & push Docker image to GHCR
  test.yml            — CI test runner
  docs.yml            — API docs generation
```

## Architecture at a Glance

**Data flow:** Newick string → parsed tree map → positioned tree (x/y coords) → SVG + metadata columns

**State management:** All shared state lives in `defonce` atoms in `app.state`. Components access state via a single React context (`app-context`) provided by `AppStateProvider`. The `use-app-state` hook returns a map of values + setter functions. `TreeContainer` derives the positioned tree (via `prepare-tree`) and passes it as props to `PhylogeneticTree`, which is a pure rendering component. `defonce` atoms survive hot reloads; `uix.dev` preload enables React fast-refresh.

**Component hierarchy:**
```
app → AppStateProvider → TreeContainer → PhylogeneticTree → Toolbar, StickyHeader, TreeNode, MetadataColumn
```

- `Toolbar` and `TreeContainer` read state from context (no prop drilling)
- `PhylogeneticTree` is a pure rendering component — all data arrives via props
- Leaf components (`TreeNode`, `Branch`, `MetadataColumn`, `StickyHeader`) receive computed data as props

## UIx Specifics

UIx is a thin Clojure-idiomatic wrapper around React 19. Key patterns used:

- `defui` — defines React components (compiles to React function components)
- `$` — JSX-like element creation: `($ :div {:style {...}} child)`
- `uix/use-state`, `uix/use-memo`, `uix/use-effect` — standard React hooks
- `uix/use-atom` — subscribes to a Clojure atom (uses `useSyncExternalStore` internally)
- `uix/create-context` / `uix/use-context` — React context API

**Important:** UIx component names follow PascalCase convention in the source (`TreeNode`, `AppStateProvider`) but are defined with `defui`.

## Common Pitfalls

1. **ClojureScript interop:** Use `(.method obj)` for method calls, not `(.-method obj)`. The latter is property access and returns the function object without calling it. Example: `(.trim s)` not `(.-trim s)`.

2. **shadow-cljs preloads:** The `:devtools {:preloads [uix.dev app.dev-preload]}` entry in `shadow-cljs.edn` enables React fast-refresh and dev-time spec instrumentation. `app.dev-preload` sets the expound printer and instruments all fdef'd functions. It must be under the `:app` build, not at the top level.

3. **Test target:** Tests use `:node-test` (runs in Node.js, not a browser). Components that touch the DOM can't be tested here — only pure functions.

4. **defonce atoms:** Don't wrap atom initialization in `def` — use `defonce` so hot reloads don't reset state. If you add new state, follow the pattern in `app.state`.

5. **Metadata first column = ID:** The first column in uploaded CSV/TSV is treated as the identifier that joins metadata to tree tip names. This is implicit (not configurable).

6. **Specs are instrumented in dev:** The `app.dev-preload` namespace calls `stest/instrument` on all fdef'd functions at load time (via shadow-cljs preloads). This provides automatic runtime checks during development. In release builds, instrumentation is stripped. Custom generators for recursive specs (e.g. `::tree-node`) live in `src/dev/app/spec_generators.cljs` — they must NOT be required from `src/main/` namespaces because `test.check` is a dev/test-only dependency.

7. **Generator isolation:** `clojure.test.check` is only on the `:dev` and `:test` classpath aliases. Never add a `test.check` require to any namespace under `src/main/`. Generator registrations (`s/with-gen`) belong in `src/dev/app/spec_generators.cljs`.

## Branch Workflow

The project uses feature branches merged via GitHub PRs. Past branches:
- `refactor` — docstrings, specs, tests, docs (PR #1)
- `state-management` — context refactor, Newick loading, hot-reload (PR #2)
- `review-specs` — spec stringency, fdefs, expound, instrumentation, custom generators, property-based tests

Create a new branch for each iteration:
```bash
git checkout -b feature-name
# ... make changes ...
git push --set-upstream origin feature-name
```

## What the Tests Cover

- **newick_test:** Tokenization, parsing of various Newick formats (with/without branch lengths, names, nested clades), edge cases (nil, empty, unnamed nodes)
- **csv_test:** Delimiter detection, quote stripping, column header generation, `parse-metadata` output shape
- **tree_test:** `count-tips`, `assign-y-coords`, `assign-x-coords`, `get-max-x`, `get-leaves`, `calculate-scale-unit`, `get-ticks`, edge cases
- **color_test:** Palette helpers, gradient/legend builders, `build-color-map`
- **date_test:** Date parsing, normalization, epoch conversion
- **scale_test:** Scale tick calculation, major/minor ticks
- **state_test:** State export/import round-trip serialization
- **specs_test:** `get-allowed-keys`, `validate-spec!`, `s/exercise` smoke tests for key specs
- **generative_test:** Property-based `defspec` tests (Newick round-trip, tree invariants) + `stest/check` tests for 8 fdef'd functions
- **arborview_import_test / nextstrain_import_test / export_html_test:** Import/export integration tests

All tests are pure-function tests. No component/rendering tests exist yet.

## Docker

A multi-stage Dockerfile builds the app with Clojure tooling and serves it with nginx:

```bash
docker build -t phylo .
docker run -p 8080:80 phylo
```

A GitHub Actions workflow (`.github/workflows/docker.yml`) builds and pushes the image to GitHub Container Registry on pushes to `main`.

## Potential Future Work

Areas the owner has been iterating on (in case they ask for "another iteration"):
- Component rendering tests (requires a browser-based test target or jsdom)
- Tree ladderizing (sorting branches by descending tips)
- Configurable metadata ID column (currently implicit first column)
- Color-coding branches or metadata by categorical values
- Tree rooting / re-rooting
- Larger-scale performance (virtualized rendering for trees with thousands of tips)
