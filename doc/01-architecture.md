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
└─────────────────┘
```

## Component Hierarchy

```
app
└── AppStateProvider              Context provider (app.state)
    └── TreeContainer             Reads context, derives positioned tree
        └── TreeViewer            Layout shell — toolbar, viewport, SVG canvas
            ├── Toolbar           File loaders, sliders, toggles (reads from context)
            ├── MetadataHeader    Sticky HTML column header labels
            └── <svg>
                ├── PixelGrid         Debug pixel coordinate grid (conditional)
                ├── ScaleGridlines    Evolutionary distance gridlines (conditional)
                ├── PhylogeneticTree  Thin wrapper — SVG group with padding transform
                │   └── TreeNode      Recursive tree rendering
                │       ├── Branch    Horizontal + vertical line segments
                │       ├── <circle>  Node marker (always on leaves, optional on internals)
                │       ├── <text>    Tip label (leaves only)
                │       └── TreeNode... Child nodes (recursive)
                └── MetadataTable     Computes column offsets, wraps columns
                    └── MetadataColumn  Per-column header + data cells with borders
```

## State Management

All shared mutable state lives in `defonce` atoms in the `app.state` namespace. This design provides two benefits:

1. **Hot-reload resilience** — `defonce` atoms survive shadow-cljs namespace reloads, so loaded trees and metadata persist across code changes.
2. **Decoupled components** — components read state via React context (`use-context`) instead of prop drilling.

### State Atoms

| Atom | Type | Default | Purpose |
|------|------|---------|---------|
| `!newick-str` | string | Small demo tree | Current Newick tree string |
| `!metadata-rows` | vector of maps | `[]` | Parsed rows from uploaded CSV/TSV |
| `!active-cols` | vector of header configs | `[]` | Column definitions with `:key`, `:label`, `:width` |
| `!x-mult` | number | `0.5` | Horizontal zoom multiplier (0.05–1.5) |
| `!y-mult` | number | `30` | Vertical tip spacing in pixels (10–100) |
| `!show-internal-markers` | boolean | `false` | Show circle markers on internal nodes |
| `!show-scale-gridlines` | boolean | `true` | Show evolutionary distance gridlines |
| `!show-pixel-grid` | boolean | `false` | Show pixel coordinate debug grid |
| `!col-spacing` | number | `0` | Extra horizontal spacing between metadata columns |

### Context Architecture

```
app
└── AppStateProvider          Reads all atoms via uix/use-atom,
    │                         bundles values + setters into a map,
    │                         provides them through app-context
    └── TreeContainer         Consumes context, calls prepare-tree
        └── TreeViewer        Layout shell — all data via props
            ├── Toolbar       Consumes context via use-app-state
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
 :show-scale-gridlines    true   :set-show-scale-gridlines!    fn
 :show-pixel-grid         false  :set-show-pixel-grid!         fn
 :col-spacing             0      :set-col-spacing!             fn}
```

Components that need shared state call `(state/use-app-state)` to get this map. Leaf rendering components (`TreeNode`, `Branch`, `MetadataColumn`, `MetadataHeader`, `ScaleGridlines`, `PixelGrid`) stay props-based since they receive computed/positioned data, not raw state.

### Derived State

The `prepare-tree` function (in `app.core`) encapsulates the full pipeline: parse Newick → assign coordinates → collect leaves → merge metadata. `TreeContainer` calls it inside a `use-memo`, recomputing only when `newick-str`, `metadata-rows`, or `active-cols` change. The result (`{:tree :tips :max-depth}`) is passed as props to `PhylogeneticTree`, which is a pure rendering component.

### Fast Refresh

The `:app` shadow-cljs build includes `uix.dev` as a preload, which integrates with `react-refresh`. Combined with the `defonce` atoms, this gives robust state preservation during development — both React component state and application data survive hot reloads.

## Layout System

The `LAYOUT` constant in `app.core` centralizes all spacing values:

| Key | Default | Purpose |
|-----|---------|---------|
| `:svg-padding-x` | 40px | Horizontal SVG padding |
| `:svg-padding-y` | 40px | Vertical SVG padding |
| `:header-height` | 36px | Metadata header bar height |
| `:label-buffer` | 150px | Space for tip labels |
| `:metadata-gap` | 40px | Gap between labels and metadata |
| `:default-col-width` | 120px | Default metadata column width |
| `:toolbar-gap` | 20px | Toolbar control spacing |
| `:node-marker-radius` | 3px | Radius of circular SVG node markers |
| `:node-marker-fill` | `#333` | Fill color for node markers |

### Coordinate Systems

- **Tree coordinates**: Branch lengths determine x-positions; sequential integers determine y-positions for leaves
- **Scaled coordinates**: Tree coordinates × `current-x-scale` (dynamic) and × `y-mult` (user-controlled)
- **SVG coordinates**: Scaled coordinates + `svg-padding-x/y` offset

## Specs

`app.specs` defines `clojure.spec.alpha` specs for all core data structures:

- `::tree-node` — parsed Newick node (recursive)
- `::positioned-node` — node with `:x` and `:y` coordinates
- `::parsed-metadata` — result of `csv/parse-metadata`
- `::app-state` — shape of the context map from `AppStateProvider`
- Component prop specs (`::branch-props`, `::tree-node-props`, etc.)
- `s/fdef` specs for key functions (`newick->map`, `count-tips`, `prepare-tree`, etc.)

## TSX Component Extraction (Work in Progress)

Pure rendering components are being translated from UIx (ClojureScript) into TypeScript/TSX. The rationale is twofold:

1. **Portability** — TSX components can be consumed by any React project, not just ClojureScript apps.
2. **Storybook testing** — TSX components can be rendered and tested in isolation using [Storybook](https://storybook.js.org).

**This is a work in progress.** The UIx implementations in `app.core` remain the canonical versions. TSX counterparts live in `src/tsx/components/` and are compiled to `src/gen/` (gitignored build artifacts). The two implementations are kept in sync, and the ClojureScript layer can import either version.

### Design Principle

TSX components are **pure functions of their props** — they have no implicit dependencies on layout constants, application state, or context. All rendering parameters are threaded through from the ClojureScript layer, which remains the single source of truth for state management and layout configuration.

### Current Status

| Component | UIx (canonical) | TSX |
|-----------|:-:|:-:|
| `Branch` | ✓ | ✓ |
| `TreeNode` | ✓ | ✓ |
| `PixelGrid` | ✓ | ✓ |
| `ScaleGridlines` | ✓ | — |
| `PhylogeneticTree` | ✓ (thin SVG wrapper) | — |
| `MetadataColumn` | ✓ | — |
| `MetadataTable` | ✓ | — |
| `MetadataHeader` | ✓ | — |
| `Toolbar` | ✓ (stateful) | — (stays in CLJS) |
| `TreeViewer` | ✓ (layout shell) | — (stays in CLJS) |
| `TreeContainer` | ✓ (context bridge) | — (stays in CLJS) |

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
| `app.state` | Shared state atoms, React context provider, `use-app-state` hook |
| `app.core` | UI components, layout algorithms, app entry point |
| `app.newick` | Recursive descent Newick parser |
| `app.csv` | CSV/TSV parsing with column metadata |
| `app.specs` | Spec definitions for data structures & functions |
