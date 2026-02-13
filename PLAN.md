# Refactoring Plan: Namespace Organization & Component Internals

Two-phase refactoring to improve code organization, eliminate duplication, fix a key performance issue, and add memoization — without changing user-facing behavior. Each step is independently safe to commit.

**Tooling notes:** Use `clj-paren-repair` after every ClojureScript file edit to fix parentheses and auto-format. Use the `clojure-eval` skill (via `clj-nrepl-eval`) to verify that edited files compile and functions still work — especially after namespace moves that update `:require` forms across many files.

---

## Phase 1 — Namespace Reorganization

### Step 1: Move `app.components.scale` → `app.scale`

Rename `src/main/app/components/scale.cljs` to `src/main/app/scale.cljs`. Update its `ns` declaration from `app.components.scale` to `app.scale`. Then update all referencing namespaces:
- `src/main/app/components/tree.cljs` — requires `app.components.scale`
- `src/main/app/components/metadata.cljs` — requires `app.components.scale`
- `src/main/app/components/viewer.cljs` — requires `app.components.scale`
- `src/test/app/scale_test.cljs` — requires `app.components.scale`

Also migrate `calculate-scale-unit` and `get-ticks` from `src/main/app/tree.cljs` into `app.scale`, since they're scale-related, not tree-related. Update dependents of those two functions:
- `src/main/app/components/viewer.cljs` — calls `tree/calculate-scale-unit`
- `src/test/app/tree_test.cljs` — tests `calculate-scale-unit` and `get-ticks` (move those tests to `scale_test.cljs`)

### Step 2: Create `app.util`

Create `src/main/app/util.cljs` containing:
- `client->svg` — currently duplicated in `src/main/app/components/viewer.cljs` and `src/main/app/components/legend.cljs`
- `clamp` — currently in `src/main/app/components/legend.cljs`

Remove the duplicates from `viewer.cljs` and `legend.cljs`; update their `:require` forms to use `app.util`.

### Step 3: Create `app.io`

Create `src/main/app/io.cljs` containing:
- `save-blob!` and `fallback-download!` — currently in `src/main/app/export/html.cljs` but used by `src/main/app/export/svg.cljs` and `src/main/app/components/selection_bar.cljs`
- `read-file!` — currently in `src/main/app/components/toolbar.cljs`

Update all dependents:
- `src/main/app/export/svg.cljs` — currently requires `app.export.html` only for `save-blob!`
- `src/main/app/components/selection_bar.cljs` — currently requires `app.export.html` only for `save-blob!`
- `src/main/app/export/html.cljs` — still uses `save-blob!` internally; switch to require from `app.io`
- `src/main/app/components/toolbar.cljs` — remove `read-file!` def, require from `app.io`

### Step 4: Extract `viewer.cljs` inline logic

From `TreeViewer*` in `src/main/app/components/viewer.cljs`, extract the following inline computations into named pure functions:

- **Legend section building** (~50 lines): Extract a `build-legend-sections` function that computes `custom-entries`, `legend-sections`, and `show-legend?` from color-by-field, color-map, and custom-highlight data. Place it in `src/main/app/color.cljs` alongside the existing `build-legend` function (natural home).
- **Box-select / lasso logic** (~60 lines of drag-selection geometry): Extract `leaves-in-rect` (or similar) as a pure function that takes positioned leaves and a bounding rectangle, returns selected tip names. Place it in `src/main/app/tree.cljs` since it's tree-geometry computation.
- **`col-gaps` computation**: This spacing calculation appears inline in both `TreeViewer*` and `MetadataTable*` in `src/main/app/components/metadata.cljs`. Extract it as a pure function in `app.scale` or `app.layout` and call from both places.

---

## Phase 2 — Component Internals & Performance

### Step 5: Fix O(n²) `get-leaves` in `TreeNode`

In `src/main/app/components/tree.cljs`, `TreeNode*` calls `tree/get-leaves` on every render for every internal node to build a `leaf-names` set used for selection state. This is O(n) per node × O(n) nodes = O(n²).

Fix: In `src/main/app/tree.cljs`, extend `prepare-tree` (or add a new pass) to precompute and attach a `:leaf-names` set to every internal node. Then `TreeNode*` reads `(:leaf-names node)` instead of calling `get-leaves`. This is a one-time O(n) traversal.

### Step 6: Add `uix/use-memo` where beneficial

In `src/main/app/components/viewer.cljs` `TreeViewer*`:
- Wrap the (newly extracted) `build-legend-sections` call in `uix/use-memo` keyed to the relevant inputs (color-by-field, color-map, highlights, custom-highlight-colors)
- Wrap `merged-highlights` computation in `uix/use-memo`
- Wrap `col-gaps` computation in `uix/use-memo`

In `src/main/app/components/metadata.cljs` `MetadataTable*`:
- Wrap `col-gaps` computation in `uix/use-memo`

In `src/main/app/components/grid.cljs` `MetadataGrid*`:
- Wrap `cols->col-defs` and `tree-ordered-rows` in `uix/use-memo` (these are called on every render but only change when metadata or tree changes)

### Step 7: Extract testable pure functions from `grid.cljs`

Make the following private functions in `src/main/app/components/grid.cljs` public so they're accessible for future testing:
- `date-comparator`
- `col-def-for-type`
- `cols->col-defs`
- `tree-ordered-rows`

No new tests in this cycle, but these become testable. Add docstrings if missing.

---

## Verification

After each step:
1. Run `clj-paren-repair` on all edited `.cljs` files
2. Use `clj-nrepl-eval` to `(require 'affected.namespace :reload)` for every changed namespace and verify no compilation errors
3. Run `npm run test` to confirm all existing tests pass
4. Manual smoke test: `npm run dev` → load a tree + metadata → verify rendering, selection, legend, export all work

After all steps:
- `npm run build` succeeds (release build catches dead code / missing requires)
- Full test suite green
- Quick manual check of HTML export (since `save-blob!` moved)

## Decisions

- **Keep `app.state` together**: Despite being 458 lines, the export/import/coercion logic is tightly coupled with the atom definitions. No split.
- **Skip new test files**: This cycle focuses on organization + optimization. Extracted functions become *testable* but writing tests is a separate iteration.
- **`calculate-scale-unit` and `get-ticks` move to `app.scale`**: They're really scale helpers that happen to live in `app.tree`. Moving them reduces `app.tree`'s responsibilities and makes `app.scale` the single home for all scale logic.
- **Legend building logic goes to `app.color`**: It's the natural home next to `build-legend` and `build-color-map`.
- **Box-select geometry goes to `app.tree`**: It's spatial computation over positioned tree nodes — fits with `get-leaves`, `prepare-tree`.
