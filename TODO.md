# TODO — Upcoming Iterations

Planning notes for future work sessions on Phylo.
Last updated: 2026-02-06

---

## Backlog

### 1. JS/TSX Component Extraction

Factor leaf rendering components out into plain JSX/TSX so they can be consumed by any React project. This may also allow us to later test components in isolation using a tool like [Storybook](https://storybook.js.org).

**Decision:** Start in-repo (e.g., `src/components/`), extract to a separate npm package later. Do not remove existing ClojureScript/UIx implementations
while working on TypeScript counterparts. Preserve existing UIx components until we're ready to (potentially) adopt TSX versions. Allow swapping components independently, potentially using a mix of TSX and UIx components.

**Components suitable for extraction (pure rendering, no state/context):**
- `Branch` — two `<line>` elements (horizontal + vertical)
- `TreeNode` — recursive SVG node renderer (branch + label + children)
- `MetadataColumn` — column of `<text>` SVG elements
- `MetadataHeader` — sticky HTML header row

**Components that stay in ClojureScript (state wiring, parsing, logic):**
- `Toolbar`, `PhylogeneticTree`, `app`, `AppStateProvider`

**Conversion notes:**
- UIx `$` macro → JSX is nearly 1:1
- ClojureScript keyword map keys (`:name`, `:branch-length`) → string/camelCase object keys
- `for` → `.map()`, `when` → `&&`, `get-in` → optional chaining
- `LAYOUT` constants → exported JS object
- Define TypeScript interfaces: `PositionedNode`, `EnrichedLeaf`, `ColumnConfig`
- Consume from ClojureScript via shadow-cljs `:npm-deps` or `:js-provider :import`

**Inspired by:** David Nolen's approach — components in JS, wiring/logic in ClojureScript. Described in [this talk](https://www.youtube.com/watch?v=3HxVMGaiZbc).

### 2. Self-Contained HTML Export

Add a button to serialize the full app + current data into a single offline HTML file.

**Feasibility: confirmed.** The app is fully self-contained (no CDN deps, no external fetches, inline styles, empty CSS file). A release-build JS bundle would be ~800 KB–1.2 MB.

**Approach:**
- Do a release build (`shadow-cljs release app`)
- Read `index.html` template and `main.js` bundle
- Inline the JS into a `<script>` tag
- Embed current app state (Newick string + metadata rows + active columns) as a JSON blob in a `<script id="phylo-data" type="application/json">` tag
- On app init, check for `#phylo-data` element — if present, hydrate state from it instead of using defaults
- Provide a "Download HTML" button in the Toolbar
- The generated file would be ~1 MB — reasonable for sharing interactive visualizations

**Open questions:**
- Should the export include the Toolbar UI, or render a "static viewer" mode?
- Could also generate a minimal HTML with just the SVG snapshot (much smaller, but not interactive)

---

## Completed

- [x] PR #1 — Docstrings, specs, tests, Codox docs (`refactor` branch)
- [x] PR #2 — State management refactor, Newick loading, hot-reload (`state-management` branch)
- [x] PR #3 — Node markers, `prepare-tree` refactor, GitHub Actions docs workflow (`update` branch)
  - Circular markers on all leaf nodes; optional markers on internal nodes via checkbox
  - Extracted `prepare-tree` function; `TreeContainer` bridges context → props; `PhylogeneticTree` is now a pure renderer
  - `.github/workflows/docs.yml` — Codox → GitHub Pages (needs Pages enabled in repo settings)