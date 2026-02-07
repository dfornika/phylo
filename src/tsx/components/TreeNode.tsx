/**
 * TreeNode — Recursive SVG tree node renderer.
 *
 * Renders a positioned tree node and all its descendants. For each node,
 * draws the branch connecting it to its parent, renders a circle marker
 * (always on leaves, optionally on internal nodes), adds a text label
 * for leaf nodes, and recurses into children.
 *
 * This is a TSX counterpart of the UIx `TreeNode` component defined
 * in app.core. The UIx version remains the canonical implementation.
 *
 * All rendering parameters arrive via props — this component has no
 * implicit dependencies on layout constants or application state.
 * The ClojureScript layer is the single source of truth for layout
 * values and passes them through as props.
 *
 * To use from ClojureScript (when ready to wire in):
 *   (:require ["/components/TreeNode" :refer (TreeNode)])
 *
 * Note: The `node` prop must be a plain JS object (not a ClojureScript
 * persistent map). The CLJS layer should convert via clj->js before
 * passing data to this component.
 */

import { Branch } from "./Branch";
import type { PositionedNode } from "./types";

interface TreeNodeProps {
  /** Positioned tree node (with x, y, children, name, id) */
  node: PositionedNode;
  /** Parent node's x coordinate (unscaled) */
  parentX: number;
  /** Parent node's y coordinate (unscaled) */
  parentY: number;
  /** Horizontal scaling factor (pixels per branch-length unit) */
  xScale: number;
  /** Vertical spacing in pixels between adjacent tips */
  yScale: number;
  /** Whether to render circle markers on internal (non-leaf) nodes */
  showInternalMarkers: boolean;
  /** Radius of the circular node marker in pixels */
  markerRadius: number;
  /** Fill color for node markers (e.g. "#333") */
  markerFill: string;
}

export function TreeNode({
  node, parentX, parentY, xScale, yScale,
  showInternalMarkers, markerRadius, markerFill,
}: TreeNodeProps) {
  const scaledX = node.x * xScale;
  const scaledY = node.y * yScale;
  const pX = parentX * xScale;
  const pY = parentY * yScale;
  const lineWidth = 0.5;
  const lineColor = "#000";
  const isLeaf = node.children.length === 0;

  return (
    <g>
      <Branch
        x={scaledX} y={scaledY}
        parentX={pX} parentY={pY}
        lineColor={lineColor} lineWidth={lineWidth}
      />

      {/* Node marker — always on leaves, optionally on internal nodes */}
      {(isLeaf || showInternalMarkers) && (
        <circle cx={scaledX} cy={scaledY} r={markerRadius} fill={markerFill} />
      )}

      {/* Tip label */}
      {isLeaf && (
        <text
          x={scaledX + 8}
          y={scaledY}
          dominantBaseline="central"
          style={{ fontFamily: "monospace", fontSize: "12px", fontWeight: "bold" }}
        >
          {node.name}
        </text>
      )}

      {/* Recurse into children */}
      {node.children.map((child) => (
        <TreeNode
          key={child.id}
          node={child}
          parentX={node.x}
          parentY={node.y}
          xScale={xScale}
          yScale={yScale}
          showInternalMarkers={showInternalMarkers}
          markerRadius={markerRadius}
          markerFill={markerFill}
        />
      ))}
    </g>
  );
}
