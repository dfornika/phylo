/**
 * PixelGrid — SVG debug grid showing pixel coordinates.
 *
 * Renders light dashed lines at regular intervals with axis labels,
 * useful for development and layout troubleshooting. Rendered in
 * raw SVG pixel space (not affected by tree transforms).
 *
 * This is a TSX counterpart of the UIx `PixelGrid` component defined
 * in app.core. The UIx version remains the canonical implementation.
 *
 * To use from ClojureScript (when ready to wire in):
 *   (:require ["/components/PixelGrid" :refer (PixelGrid)])
 */

interface PixelGridProps {
  /** SVG canvas width in pixels */
  width: number;
  /** SVG canvas height in pixels */
  height: number;
  /** Grid line spacing in pixels (default 50) */
  spacing?: number;
}

export function PixelGrid({ width, height, spacing = 50 }: PixelGridProps) {
  const gridColor = "#bdf";
  const labelColor = "#8ab";

  const vLines: number[] = [];
  for (let x = 0; x <= width; x += spacing) vLines.push(x);

  const hLines: number[] = [];
  for (let y = 0; y <= height; y += spacing) hLines.push(y);

  return (
    <g className="pixel-grid">
      {/* Vertical lines */}
      {vLines.map((x) => (
        <line key={`pgv-${x}`}
              x1={x} y1={0} x2={x} y2={height}
              stroke={gridColor} strokeDasharray="2 4" strokeWidth={0.5} />
      ))}

      {/* Horizontal lines */}
      {hLines.map((y) => (
        <line key={`pgh-${y}`}
              x1={0} y1={y} x2={width} y2={y}
              stroke={gridColor} strokeDasharray="2 4" strokeWidth={0.5} />
      ))}

      {/* X-axis labels (top edge) */}
      {vLines.map((x) =>
        x > 0 ? (
          <text key={`pgxl-${x}`}
                x={x} y={8} textAnchor="middle"
                style={{ fontFamily: "monospace", fontSize: "8px", fill: labelColor }}>
            {x}
          </text>
        ) : null
      )}

      {/* Y-axis labels (left edge) */}
      {hLines.map((y) =>
        y > 0 ? (
          <text key={`pgyl-${y}`}
                x={2} y={y - 2}
                style={{ fontFamily: "monospace", fontSize: "8px", fill: labelColor }}>
            {y}
          </text>
        ) : null
      )}
    </g>
  );
}
