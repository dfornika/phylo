/**
 * Shared TypeScript interfaces for Phylo tree data structures.
 *
 * These mirror the clojure.spec definitions in app.specs.
 * When data crosses the CLJS → TSX boundary, the CLJS layer
 * converts ClojureScript maps to plain JS objects matching
 * these interfaces (via clj->js or manual conversion).
 */

/** A positioned tree node with layout coordinates assigned. */
export interface PositionedNode {
  /** Unique numeric identifier assigned during layout */
  id: number;
  /** Taxon name — present on leaf nodes, may be null/empty on internal nodes */
  name: string | null;
  /** Branch length from parent (may be null if not in the Newick string) */
  branchLength: number | null;
  /** x coordinate (horizontal, proportional to evolutionary distance) */
  x: number;
  /** y coordinate (vertical, evenly spaced for leaves) */
  y: number;
  /** Child nodes — empty array for leaf nodes */
  children: PositionedNode[];
}
