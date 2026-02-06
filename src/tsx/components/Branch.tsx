/**
 * Branch — SVG tree branch component.
 *
 * Renders a single phylogenetic tree branch as two SVG lines:
 * a horizontal segment (the branch itself) and a vertical connector
 * to the parent node.
 *
 * This is a TSX counterpart of the UIx `Branch` component defined
 * in app.core. The UIx version remains the canonical implementation.
 *
 * To use from ClojureScript (when ready to wire in):
 *   (:require ["/components/Branch" :refer (Branch)])
 *
 * UIx's $ macro auto-converts kebab-case props to camelCase for
 * non-UIx components, so existing call sites need minimal changes:
 *   ($ Branch {:x 10 :y 20 :parent-x 0 :parent-y 0
 *              :line-color "#000" :line-width 0.5})
 */

interface BranchProps {
  /** Endpoint (child) x coordinate */
  x: number;
  /** Endpoint (child) y coordinate */
  y: number;
  /** Start (parent) x coordinate */
  parentX: number;
  /** Start (parent) y coordinate */
  parentY: number;
  /** Stroke color string (e.g. "#000") */
  lineColor: string;
  /** Stroke width in pixels */
  lineWidth: number;
}

export function Branch({ x, y, parentX, parentY, lineColor, lineWidth }: BranchProps) {
  return (
    <g>
      {/* Horizontal branch */}
      <line x1={parentX} y1={y} x2={x} y2={y}
            stroke={lineColor} strokeWidth={lineWidth} />
      {/* Vertical connector to siblings */}
      <line x1={parentX} y1={parentY} x2={parentX} y2={y}
            stroke={lineColor} strokeWidth={lineWidth} />
    </g>
  );
}
