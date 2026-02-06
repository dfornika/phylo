# Architecture

## Overview

Phylo is a single-page ClojureScript application that renders phylogenetic trees as SVG with optional metadata overlays. It uses [UIx](https://github.com/pitch-io/uix) as a thin Clojure-idiomatic wrapper around React 19.

## Data Flow

```
┌─────────────────┐
│ Newick string    │  Plain text input (e.g. "(A:0.1,B:0.2)Root;")
└────────┬────────┘
         │ newick/newick->map
         ▼
┌─────────────────┐
│ Tree map         │  {:name "Root" :branch-length 0.3
│ (recursive)      │   :children [{:name "A" ...} {:name "B" ...}]}
└────────┬────────┘
         │ assign-y-coords + assign-x-coords
         ▼
┌─────────────────┐
│ Positioned tree  │  Same structure with :x and :y added to every node
└────────┬────────┘
         │ get-leaves → merge metadata from CSV
         ▼
┌─────────────────┐
│ Enriched leaves  │  Leaf nodes with :metadata map from uploaded CSV
└────────┬────────┘
         │ PhylogeneticTree component
         ▼
┌─────────────────┐
│ SVG + HTML       │  TreeNode (branches/labels) + MetadataColumn (values)
└─────────────────┘
```

## Component Hierarchy

```
app
└── PhylogeneticTree          Main container, manages state
    ├── Toolbar               Sliders + file upload
    ├── MetadataHeader        Sticky column header labels
    └── <svg>
        ├── gridlines         Scale bar gridlines
        ├── TreeNode          Recursive tree rendering
        │   ├── Branch        Horizontal + vertical line segments
        │   ├── <text>        Tip label (leaves only)
        │   └── TreeNode...   Child nodes (recursive)
        └── MetadataColumn    Per-column text values aligned to tips
```

## State Management

All mutable state lives inside UIx `use-state` hooks within the `PhylogeneticTree` component:

| State | Type | Purpose |
|-------|------|---------|
| `x-mult` | number | Horizontal zoom multiplier (0.05–1.5) |
| `y-mult` | number | Vertical spacing in pixels (10–100) |
| `metadata-rows` | vector of maps | Parsed rows from uploaded CSV/TSV |
| `active-cols` | vector of header configs | Column definitions with `:key`, `:label`, `:width` |

Tree parsing and metadata merging happen inside `use-memo`, recomputing only when `newick-str`, `metadata-rows`, or `active-cols` change.

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

### Coordinate Systems

- **Tree coordinates**: Branch lengths determine x-positions; sequential integers determine y-positions for leaves
- **Scaled coordinates**: Tree coordinates × `current-x-scale` (dynamic) and × `y-mult` (user-controlled)
- **SVG coordinates**: Scaled coordinates + `svg-padding-x/y` offset

## Specs

`app.specs` defines `clojure.spec.alpha` specs for all core data structures:

- `::tree-node` — parsed Newick node (recursive)
- `::positioned-node` — node with `:x` and `:y` coordinates
- `::parsed-metadata` — result of `csv/parse-metadata`
- Component prop specs (`::branch-props`, `::toolbar-props`, etc.)
- `s/fdef` specs for key functions (`newick->map`, `count-tips`, etc.)

## Namespaces

| Namespace | Purpose |
|-----------|---------|
| `app.core` | UI components, layout algorithms, app entry point |
| `app.newick` | Recursive descent Newick parser |
| `app.csv` | CSV/TSV parsing with column metadata |
| `app.specs` | Spec definitions for data structures & functions |
