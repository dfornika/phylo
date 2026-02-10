# Architecture

## Overview

Phylo is a single-page ClojureScript application that renders phylogenetic trees as SVG with optional metadata overlays. It uses [UIx](https://github.com/pitch-io/uix) as a thin Clojure-idiomatic wrapper around React 19.

## Data Flow

```
┌─────────────────┐
│ Newick string   │  Plain text input (e.g. "(A:0.1,B:0.2)Root;")
└────────┬────────┘
         │ newick/newick->map
         ▼
┌─────────────────┐
│ Tree map        │  {:name "Root" :branch-length 0.3
│ (recursive)     │   :children [{:name "A" ...} {:name "B" ...}]}
└────────┬────────┘
         │ assign-y-coords + assign-x-coords
         ▼
┌─────────────────┐
│ Positioned tree │  Same structure with :x and :y added to every node
└────────┬────────┘
         │ get-leaves → merge metadata from CSV
         ▼
┌─────────────────┐
│ Enriched leaves │  Leaf nodes with :metadata map from uploaded CSV
└────────┬────────┘
         │ TreeViewer component
         ▼
┌─────────────────┐
│ SVG + HTML      │  PhylogeneticTree → TreeNode (branches/labels)
│                 │  MetadataTable → MetadataColumn (values)
│                 │  MetadataGrid (AG-Grid table)
└─────────────────┘
```

## Component Hierarchy

```
app                               (app.core)
└── AppStateProvider              (app.state) Context provider
    └── TreeContainer             (app.components.viewer) Reads context, derives positioned tree
        └── TreeViewer            (app.components.viewer) Layout shell — toolbar, viewport, SVG canvas
            ├── Toolbar           (app.components.toolbar) File loaders, sliders, toggles (reads from context)
            ├── StickyHeader      (app.components.metadata) Sticky HTML column header labels + scale
            ├── <svg>             (box-select: drag to lasso leaves)
            │   ├── PixelGrid         (app.components.viewer) Debug pixel coordinate grid (conditional)
            │   ├── ScaleBar          (app.components.viewer) Solid scale bar + tick labels
            │   ├── ScaleGridlines    (app.components.viewer) Evolutionary distance gridlines (conditional)
            │   ├── PhylogeneticTree  (app.components.tree) Thin wrapper — SVG group with padding transform
            │   │   └── TreeNode      (app.components.tree) Recursive tree rendering
            │   │       ├── Branch    (app.components.tree) Horizontal + vertical line segments
            │   │       ├── <circle>  Node marker (clickable on leaves to toggle selection)
            │   │       ├── <text>    Tip label (leaves only, clickable to toggle selection)
            │   │       └── TreeNode... Child nodes (recursive)
            │   └── MetadataTable     (app.components.metadata) Computes column offsets, wraps columns
            │       └── MetadataColumn  (app.components.metadata) Per-column header + data cells with borders
            ├── SelectionBar      (app.components.selection_bar) Highlight color picker + assign/clear buttons
            └── ResizablePanel    (app.components.resizable_panel) Draggable resize handle wrapper
                └── MetadataGrid  (app.components.grid) AG-Grid table with editing, selection sync
```

## State Management

All shared mutable state lives in `defonce` atoms in the `app.state` namespace. This design provides two benefits:

1. **Hot-reload resilience** — `defonce` atoms survive shadow-cljs namespace reloads, so loaded trees and metadata persist across code changes.
2. **Decoupled components** — components read state via React context (`use-context`) instead of prop drilling.

### State Atoms

| Atom | Type | Default | Purpose |
|------|------|---------|---------|
| `!newick-str` | string | `nil` | Current Newick tree string |
| `!metadata-rows` | vector of maps | `[]` | Parsed rows from uploaded CSV/TSV |
| `!active-cols` | vector of header configs | `[]` | Column definitions with `:key`, `:label`, `:width` |
| `!x-mult` | number | `0.5` | Horizontal zoom multiplier (0.05–1.5) |
| `!y-mult` | number | `30` | Vertical tip spacing in pixels (10–100) |
| `!show-internal-markers` | boolean | `false` | Show circle markers on internal nodes |
| `!show-scale-gridlines` | boolean | `false` | Show evolutionary distance gridlines |
| `!show-branch-lengths` | boolean | `false` | Show internal node distance labels |
| `!scale-origin` | keyword | `:tips` | Scale origin for labels (`:tips` or `:root`) |
| `!show-pixel-grid` | boolean | `false` | Show pixel coordinate debug grid |
| `!col-spacing` | number | `0` | Extra horizontal spacing between metadata columns |
| `!highlight-color` | string | `"#4682B4"` | Brush color for painting highlights onto selected leaves |
| `!selected-ids` | set | `#{}` | Set of leaf names currently selected (transient, checkbox-driven) |
| `!highlights` | map | `{}` | Persistent highlight assignments `{leaf-name → CSS color}` |

### Context Architecture

```
app
└── AppStateProvider          Reads all atoms via uix/use-atom,
    │                         bundles values + setters into a map,
    │                         provides them through app-context
    └── TreeContainer         Consumes context, calls prepare-tree
        └── TreeViewer        Layout shell — all data via props
            ├── Toolbar       Consumes context via use-app-state
            ├── SelectionBar  Consumes context via use-app-state
            └── ...           (leaf components receive computed
                               values as props — no context needed)
```

`AppStateProvider` subscribes to each atom with `uix/use-atom` (which uses React's `useSyncExternalStore` internally) and exposes the context map:

```clojure
{:newick-str              "..."  :set-newick-str!              fn
 :metadata-rows           [...]  :set-metadata-rows!           fn
 :active-cols             [...]  :set-active-cols!             fn
 :x-mult                  0.5    :set-x-mult!                  fn
 :y-mult                  30     :set-y-mult!                  fn
 :show-internal-markers   false  :set-show-internal-markers!   fn
 :show-scale-gridlines    false  :set-show-scale-gridlines!    fn
 :show-branch-lengths     false  :set-show-branch-lengths!     fn
 :scale-origin            :tips  :set-scale-origin!            fn
 :show-pixel-grid         false  :set-show-pixel-grid!         fn
 :col-spacing             0      :set-col-spacing!             fn
 :highlight-color         "..."  :set-highlight-color!         fn
 :selected-ids            #{}    :set-selected-ids!            fn
 :highlights              {}     :set-highlights!              fn}
```

Components that need shared state call `(state/use-app-state)` to get this map. Leaf rendering components (`TreeNode`, `Branch`, `MetadataColumn`, `StickyHeader`, `ScaleGridlines`, `PixelGrid`) stay props-based since they receive computed/positioned data, not raw state.

**Note:** `set-selected-ids!` accepts both a direct value (`reset!`) and an updater function (`swap!`). This supports both the grid's `onSelectionChanged` (which passes a full replacement set) and the tree's `toggle-selection` (which passes a function that adds/removes a single leaf).

### Highlight Model

Phylo uses a two-tier highlight model:

1. **`selected-ids`** — a transient `#{set}` of leaf names currently selected via AG-Grid row checkboxes or tree leaf clicks. Selection is bidirectional: clicking a leaf toggles it in `selected-ids`, and a `use-effect` in `MetadataGrid` programmatically syncs AG-Grid checkboxes to match. A `syncing-ref` guard prevents circular updates.

2. **`highlights`** — a persistent `{leaf-name → CSS-color}` map representing committed highlight assignments. Users select leaves, pick a brush color via `SelectionBar`, and click "Assign" to stamp the current `highlight-color` onto every leaf in `selected-ids`.

Tree nodes render a colored circle for highlighted leaves and a dashed selection ring for selected leaves. Both can be active simultaneously.

### Cell Editing

All metadata columns except the ID (first) column support inline editing in the AG-Grid table. Double-click a cell to enter edit mode; press Enter to commit or Escape to cancel. Edits flow back through `set-metadata-rows!`, which triggers `prepare-tree` to recompute enriched tips. Both the grid and the SVG metadata overlay update in sync.

### Box Selection

Users can click and drag on the SVG background to draw a selection rectangle (lasso). Leaf nodes whose marker positions fall inside the box are added to `selected-ids`. Hold Shift to add to the existing selection instead of replacing it. The selection rectangle uses `DOMPoint.matrixTransform(getScreenCTM().inverse())` for accurate SVG coordinate conversion even when the viewport is scrolled.

### Derived State

The `prepare-tree` function (in `app.tree`) encapsulates the full pipeline: parse Newick → assign coordinates → collect leaves → merge metadata. `TreeContainer` calls it inside a `use-memo`, recomputing only when `newick-str`, `metadata-rows`, or `active-cols` change. The result (`{:tree :tips :max-depth}`) is passed as props to `PhylogeneticTree`, which is a pure rendering component.

### Fast Refresh

The `:app` shadow-cljs build includes `uix.dev` as a preload, which integrates with `react-refresh`. Combined with the `defonce` atoms, this gives robust state preservation during development — both React component state and application data survive hot reloads.


## Standalone HTML Export

Phylo can export a fully self-contained HTML snapshot that embeds the current
app bundle, styles, and state so the viewer can be reopened offline with the
same tree, metadata, and visual settings.

### Export Pipeline

1. `Toolbar` collects all runtime scripts (`<script src=...>`) and stylesheets
   (`<link rel="stylesheet">`) from the current document.
2. Each script and stylesheet is fetched and inlined directly into the export.
3. The current app state is serialized via `state/export-state` and embedded
   into the HTML as an EDN payload in a `<script id="phylo-export-state">` tag.
4. Static assets (currently `images/logo.svg`) are fetched and embedded as data
   URLs in `window.__PHYLO_ASSET_MAP__` for offline rendering.
5. The resulting HTML file is saved as `phylo-viewer.html`.

### State Serialization

`app.state/export-state` returns a versioned map of the exportable state, and
`app.state/apply-export-state!` rehydrates it. Missing keys default safely so
older exports continue to load as the app evolves.

### Bootstrapping on Load

`app.core/init` looks for the embedded EDN payload and applies it before the
initial render. This allows exported HTML files to restore state immediately.

### Asset Resolution

`TreeViewer` resolves the logo source using an asset map if present, so the
exported HTML does not rely on external files.

## ArborView Import

Phylo can ingest ArborView-style HTML exports. The toolbar accepts ArborView
HTML, extracts the embedded Newick tree and metadata table, and loads them
using the same parsing pipeline as regular Newick/CSV inputs.


## Layout System

The `LAYOUT` constant in `app.layout` centralizes all spacing values:

| Key | Default | Purpose |
|-----|---------|---------|
| `:svg-padding-x` | 40px | Horizontal SVG padding |
| `:svg-padding-y` | 40px | Vertical SVG padding |
| `:header-height` | 36px | Metadata header bar height |
| `:label-buffer` | 150px | Space for tip labels |
| `:metadata-gap` | 20px | Gap between labels and metadata |
| `:default-col-width` | 120px | Default metadata column width |
| `:toolbar-gap` | 20px | Toolbar control spacing |
| `:node-marker-radius` | 3px | Radius of circular SVG node markers |
| `:node-marker-fill` | `#333` | Fill color for node markers |

### Coordinate Systems

- **Tree coordinates**: Branch lengths determine x-positions; sequential integers determine y-positions for leaves
- **Scaled coordinates**: Tree coordinates × `current-x-scale` (dynamic) and × `y-mult` (user-controlled)
- **SVG coordinates**: Scaled coordinates + `svg-padding-x/y` offset

## Scale System

The scale bar, sticky header, and gridlines share the same tick calculation logic.
Ticks are computed with a minimum pixel spacing for labels and optional minor ticks
between major labels. Scale labels can be anchored to either the root or tips;
when anchored to tips, the tick positions are mirrored so the scale starts at 0
at the leaves, but the tick values stay on "nice" intervals. Internal node labels
use the same origin-aware mapping so they stay consistent with the scale.

## Specs

`app.specs` defines `clojure.spec.alpha` specs for all core data structures:

- `::tree-node` — parsed Newick node (recursive)
- `::positioned-node` — node with `:x` and `:y` coordinates
- `::parsed-metadata` — result of `csv/parse-metadata`
- `::app-state` — shape of the context map from `AppStateProvider`
- Component prop specs (`::branch-props`, `::tree-node-props`, `::metadata-grid-props`, `::resizable-panel-props`, etc.)
- `s/fdef` specs for key functions (`newick->map`, `count-tips`, `prepare-tree`, etc.)

## TSX Component Extraction (Work in Progress)

Pure rendering components are being translated from UIx (ClojureScript) into TypeScript/TSX. The rationale is twofold:

1. **Portability** — TSX components can be consumed by any React project, not just ClojureScript apps.
2. **Storybook testing** — TSX components can be rendered and tested in isolation using [Storybook](https://storybook.js.org).

**This is a work in progress.** The UIx implementations in the `app.components.*` namespaces remain the canonical versions. TSX counterparts live in `src/tsx/components/` and are compiled to `src/gen/` (gitignored build artifacts). The two implementations are kept in sync, and the ClojureScript layer can import either version.

### Design Principle

TSX components are **pure functions of their props** — they have no implicit dependencies on layout constants, application state, or context. All rendering parameters are threaded through from the ClojureScript layer, which remains the single source of truth for state management and layout configuration.

### Current Status

| Component | UIx (canonical) | Namespace | TSX |
|-----------|:-:|-----------|:-:|
| `Branch` | ✓ | `app.components.tree` | ✓ |
| `TreeNode` | ✓ | `app.components.tree` | ✓ |
| `PixelGrid` | ✓ | `app.components.viewer` | ✓ |
| `ScaleGridlines` | ✓ | `app.components.viewer` | — |
| `PhylogeneticTree` | ✓ (thin SVG wrapper) | `app.components.tree` | — |
| `MetadataColumn` | ✓ | `app.components.metadata` | — |
| `MetadataTable` | ✓ | `app.components.metadata` | — |
| `StickyHeader` | ✓ | `app.components.metadata` | — |
| `Toolbar` | ✓ (stateful) | `app.components.toolbar` | — (stays in CLJS) |
| `SelectionBar` | ✓ (stateful) | `app.components.selection_bar` | — (stays in CLJS) |
| `MetadataGrid` | ✓ (stateful) | `app.components.grid` | — (stays in CLJS) |
| `ResizablePanel` | ✓ (stateful) | `app.components.resizable_panel` | — (stays in CLJS) |
| `TreeViewer` | ✓ (layout shell) | `app.components.viewer` | — (stays in CLJS) |
| `TreeContainer` | ✓ (context bridge) | `app.components.viewer` | — (stays in CLJS) |

### Build Pipeline

```
src/tsx/          TSX source files (version-controlled)
    └── components/
        ├── types.ts        Shared interfaces (PositionedNode, etc.)
        ├── Branch.tsx      SVG branch lines
        └── TreeNode.tsx    Recursive tree node renderer
        ↓ npm run tsx:build (tsc)
src/gen/          Compiled JS + .d.ts (gitignored)
    └── components/
        ├── Branch.js
        ├── TreeNode.js
        └── ...
```

shadow-cljs picks up the compiled JS from `src/gen/` (on the classpath). To import from ClojureScript:

```clojure
(:require ["/components/Branch" :refer (Branch)])
```

UIx's `$` macro auto-converts kebab-case props to camelCase for non-UIx components, so `:parent-x` becomes `parentX` — matching the TSX interfaces.

## Namespaces

| Namespace | Purpose |
|-----------|---------|
| `app.core` | Thin app shell — mounts root component, provides `init` / `re-render` entry points |
| `app.state` | Shared state atoms, React context provider, `use-app-state` hook |
| `app.layout` | `LAYOUT` constant — spacing, padding, marker sizes used across all component namespaces |
| `app.tree` | Pure tree layout functions (`assign-y/x-coords`, `prepare-tree`, `get-leaves`, etc.) |
| `app.newick` | Recursive descent Newick parser |
| `app.csv` | CSV/TSV parsing with column metadata and data type detection |
| `app.specs` | Spec definitions for data structures & functions |
| `app.components.tree` | `Branch`, `TreeNode`, `PhylogeneticTree` — SVG tree rendering |
| `app.components.metadata` | `StickyHeader`, `MetadataColumn`, `MetadataTable` — SVG metadata overlay |
| `app.components.scale` | Scale tick helpers and origin-aware label mapping |
| `app.components.toolbar` | `Toolbar`, `read-file!` — user controls |
| `app.components.viewer` | `TreeContainer`, `TreeViewer`, `ScaleGridlines`, `ScaleBar`, `PixelGrid` — top-level composition |
| `app.components.grid` | `MetadataGrid` — AG-Grid table with bidirectional selection sync |
| `app.components.selection_bar` | `SelectionBar` — highlight color picker and assign/clear actions |
| `app.components.resizable_panel` | `ResizablePanel` — draggable-resize wrapper for bottom panel |
