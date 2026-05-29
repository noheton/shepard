/**
 * useLineageGraph — shared graph primitives for ECharts graph visualisations.
 *
 * Extracted from CollectionLineageGraph.vue and DataObjectProvGraph.vue (UI16).
 * Both components share: status/action colour palettes, a label-truncation
 * function, and the common ECharts series fragments.
 *
 * Usage:
 *   import { truncateLabel, STATUS_COLORS, ACTION_COLORS, DEFAULT_NODE_COLOR, baseGraphSeriesConfig }
 *     from "~/composables/useLineageGraph";
 */

// ---------------------------------------------------------------------------
// Colour palettes
// ---------------------------------------------------------------------------

/** Colour per DataObject status — used in CollectionLineageGraph */
export const STATUS_COLORS: Record<string, string> = {
  DRAFT:     "#8C8C8C",
  IN_REVIEW: "#FCA54D",
  READY:     "#4097CC",
  PUBLISHED: "#7ECA8F",
  ARCHIVED:  "#B799DB",
};

/** Colour per provenance action kind — used in DataObjectProvGraph */
export const ACTION_COLORS: Record<string, string> = {
  CREATE:  "#7ECA8F",
  UPDATE:  "#4097CC",
  DELETE:  "#E56874",
  READ:    "#8C8C8C",
  EXECUTE: "#B799DB",
};

/** Fallback colour for unknown node types */
export const DEFAULT_NODE_COLOR = "#8C8C8C";

/**
 * Look up the colour for a given node-type key.
 *
 * @param type    - The key to look up (e.g. "DRAFT" or "CREATE").
 * @param palette - Which palette to consult (defaults to STATUS_COLORS).
 * @returns The hex colour string, or DEFAULT_NODE_COLOR if the key is missing.
 */
export function nodeColor(
  type: string,
  palette: Record<string, string> = STATUS_COLORS,
): string {
  return palette[type] ?? DEFAULT_NODE_COLOR;
}

// ---------------------------------------------------------------------------
// Label truncation
// ---------------------------------------------------------------------------

/**
 * Truncate a label to at most `maxLen` characters (inclusive).
 *
 * If the label is longer than `maxLen` characters, the returned string is
 * `label.slice(0, maxLen - 2) + "…"` (so the total length is still maxLen−1
 * character + 1 ellipsis = maxLen−1 displayed glyphs + the "…" character).
 *
 * CollectionLineageGraph uses maxLen = 18 (default).
 * DataObjectProvGraph uses maxLen = 16.
 *
 * @param label  - The raw label string.
 * @param maxLen - Threshold length; labels equal to or shorter are returned as-is.
 */
export function truncateLabel(label: string, maxLen = 18): string {
  return label.length > maxLen ? label.slice(0, maxLen - 2) + "…" : label;
}

// ---------------------------------------------------------------------------
// Shared ECharts series fragment
// ---------------------------------------------------------------------------

/**
 * Returns the ECharts `series` properties shared by every Shepard graph.
 *
 * These are the fields both CollectionLineageGraph (layout:"none") and
 * DataObjectProvGraph (layout:"force") have in common.  The caller spreads
 * this result and adds its own `layout`, `nodes`, `edges`, and any layout-
 * specific overrides.
 *
 * The returned object intentionally omits `layout`, `nodes`, and `edges` so
 * callers are forced to supply them — preventing accidental use of stale data.
 */
export function baseGraphSeriesConfig(): Record<string, unknown> {
  return {
    type:           "graph",
    roam:           true,
    edgeSymbol:     ["none", "arrow"] as [string, string],
    edgeSymbolSize: [0, 8] as [number, number],
    emphasis: {
      focus: "adjacency",
    },
    lineStyle: { curveness: 0.15 },
    label: {
      color: "inherit",
    },
  };
}
