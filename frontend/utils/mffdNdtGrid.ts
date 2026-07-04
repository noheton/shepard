/**
 * MFFD-NDT-GRID — pure helpers for the 14×14 thermography coverage widget.
 *
 * The MFFD upper-shell thermography campaign uses a Section × Module
 * grid (S1..S14 × M1..M14 = up to 196 surface tiles). The tier-1
 * `shepard-plugin-fileformat-thermography` parser (commit ab8a38e13
 * and friends on `main`) writes four annotations to the parent
 * DataObject of each uploaded `.OTvis` file:
 *
 *   - urn:shepard:mffd:section  (e.g. "S4")
 *   - urn:shepard:mffd:module   (e.g. "M13")
 *   - urn:shepard:mffd:layer    (e.g. "L18")
 *   - urn:shepard:mffd:frame    (e.g. "F4")
 *
 * QS classification (when present) is annotated as:
 *   - urn:shepard:mffd:qsClassification  ("OK" / "NOK" / "unknown")
 *
 * The widget aggregates client-side: walks every DataObject in the
 * Collection, pulls its annotations, buckets by (section, module),
 * paints the grid by measurement count + flags failure.
 *
 * This file contains only pure helpers — no I/O, no Vue, no API
 * client. All helpers are unit-tested in
 * `frontend/tests/unit/mffdNdtGrid.test.ts`.
 *
 * The component file (`MffdNdtGridCard.vue`) handles the fetch +
 * rendering layer and consumes these helpers.
 */
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { colormapRgb } from "./colormap";

// ─── Predicate IRIs (mirrors plugins/.../ThermographyAnnotations.java) ────
//
// Kept in sync with the canonical Java constants. If those constants
// change, this file changes in the same PR.

export const MFFD_SECTION_IRI = "urn:shepard:mffd:section";
export const MFFD_MODULE_IRI = "urn:shepard:mffd:module";
export const MFFD_LAYER_IRI = "urn:shepard:mffd:layer";
export const MFFD_FRAME_IRI = "urn:shepard:mffd:frame";

/**
 * QS classification predicate.
 *
 * Documented in `aidocs/agent-findings/mffd-wiki-analysis-findings.md`
 * (`urn:shepard:mffd:qsClassification`, value vocabulary
 * "OK" / "NOK" / "unknown"). The OTvis tier-1 parser does NOT yet
 * emit this predicate (only Section/Module/Layer/Frame), so in
 * practice no DataObject will currently carry it.
 *
 * `hasFailedMeasurement` therefore returns `false` for every
 * real-data cell today; once the predicate is seeded (either by
 * the QS Auswertung importer or manual annotation), the red-border
 * affordance lights up with zero further widget changes.
 */
export const MFFD_QS_CLASSIFICATION_IRI = "urn:shepard:mffd:qsClassification";

// ─── Public shape: per-cell aggregated data ───────────────────────────────

export interface GridPosition {
  section: string;
  module: string;
  layer: string;
  frame: string;
}

/**
 * Minimal DataObject shape consumed by the helpers — appId, name + the
 * annotations resolved for it. The component fetches DOs via the v2
 * listDataObjects endpoint and pairs each one with annotations from
 * listAnnotations (v2 SemanticAnnotationsApi).
 */
export interface DataObjectWithAnnotations {
  appId: string;
  name: string;
  annotations: SemanticAnnotation[];
}

export interface CellMeasurement {
  dataObject: DataObjectWithAnnotations;
  layer: string;
  frame: string;
  /** "OK" | "NOK" | "unknown" | null (null when not annotated). */
  qsClassification: string | null;
}

export interface GridCellData {
  section: string;
  module: string;
  measurements: CellMeasurement[];
  /** Distinct layers covered by this cell, sorted ascending by numeric suffix. */
  layers: string[];
  /** Convenience: any "NOK" classification present. */
  anyFailed: boolean;
}

// ─── extractGridPosition ──────────────────────────────────────────────────

/**
 * Extract the 4-tuple grid position from an annotation set.
 *
 * Returns null when any of the four predicates is missing or empty;
 * a DataObject with only Section + Module (no Layer / Frame) is not
 * a complete thermography measurement and is excluded from the
 * coverage grid.
 *
 * When the same predicate is repeated on a DO (e.g. multiple OTvis
 * uploads in the same DO with different layers), the FIRST value wins.
 * In practice the OTvis parser writes one annotation per upload onto
 * the parent DO, so a multi-layer DO will surface its first layer
 * here. The full layer list is reconstructed at the cell-aggregation
 * step (`bucketByGrid`) by combining all DOs that landed in the cell.
 */
export function extractGridPosition(
  annotations: SemanticAnnotation[],
): GridPosition | null {
  const find = (iri: string): string | null => {
    for (const a of annotations) {
      if (a.propertyIRI === iri) {
        const v = (a.valueName ?? "").trim();
        if (v.length > 0) return v;
      }
    }
    return null;
  };
  const section = find(MFFD_SECTION_IRI);
  const module = find(MFFD_MODULE_IRI);
  const layer = find(MFFD_LAYER_IRI);
  const frame = find(MFFD_FRAME_IRI);
  if (!section || !module || !layer || !frame) return null;
  return { section, module, layer, frame };
}

/**
 * Pull the qsClassification value from an annotation set, or null
 * when the predicate is not present.
 */
export function extractQsClassification(
  annotations: SemanticAnnotation[],
): string | null {
  for (const a of annotations) {
    if (a.propertyIRI === MFFD_QS_CLASSIFICATION_IRI) {
      const v = (a.valueName ?? "").trim();
      if (v.length > 0) return v;
    }
  }
  return null;
}

// ─── bucketByGrid ─────────────────────────────────────────────────────────

/**
 * Stable key for a (section, module) cell. The bucketing map uses
 * these as keys; the component renders cells by iterating
 * S1..S14 × M1..M14 and reading the matching bucket.
 */
export function cellKey(section: string, module: string): string {
  return `${section}|${module}`;
}

/**
 * Aggregate DataObjects by (section, module) cell.
 *
 * Output keys are produced by `cellKey()`. A DataObject without a
 * complete grid position is silently skipped — it will not appear
 * in any cell.
 */
export function bucketByGrid(
  dataObjects: DataObjectWithAnnotations[],
): Map<string, GridCellData> {
  const buckets = new Map<string, GridCellData>();
  for (const dataObject of dataObjects) {
    const pos = extractGridPosition(dataObject.annotations);
    if (!pos) continue;
    const key = cellKey(pos.section, pos.module);
    let cell = buckets.get(key);
    if (!cell) {
      cell = {
        section: pos.section,
        module: pos.module,
        measurements: [],
        layers: [],
        anyFailed: false,
      };
      buckets.set(key, cell);
    }
    const qs = extractQsClassification(dataObject.annotations);
    cell.measurements.push({
      dataObject,
      layer: pos.layer,
      frame: pos.frame,
      qsClassification: qs,
    });
    if (qs === "NOK") cell.anyFailed = true;
  }
  // Materialise sorted distinct layer list per cell.
  for (const cell of buckets.values()) {
    const seen = new Set<string>();
    for (const m of cell.measurements) seen.add(m.layer);
    cell.layers = Array.from(seen).sort(compareLayer);
  }
  return buckets;
}

// ─── compareLayer ─────────────────────────────────────────────────────────

/**
 * Compare layer labels (e.g. "L8" < "L19" < "L19+") by their numeric
 * prefix. Lexicographic sort would put "L19" before "L8"; we want the
 * natural process-stage order.
 *
 * Labels without a parseable numeric portion sort lexicographically
 * after the parseable ones, preserving determinism.
 */
export function compareLayer(a: string, b: string): number {
  const na = parseLayerNum(a);
  const nb = parseLayerNum(b);
  if (na !== null && nb !== null) {
    if (na !== nb) return na - nb;
    return a.localeCompare(b);
  }
  if (na !== null) return -1;
  if (nb !== null) return 1;
  return a.localeCompare(b);
}

function parseLayerNum(label: string): number | null {
  const m = /^L(\d+)/.exec(label);
  if (!m) return null;
  const n = parseInt(m[1] ?? "", 10);
  return Number.isFinite(n) ? n : null;
}

// ─── colourForCount ───────────────────────────────────────────────────────

/**
 * Map a measurement count to a CSS rgb(...) string.
 *
 * Uses the inferno colormap (already imported in the project for
 * Trace3D rendering). 0 → returns the empty-cell sentinel; otherwise
 * the count is normalised to [0, 1] relative to `max` and mapped
 * through inferno (dark-purple → orange → yellow at the top of the
 * range).
 *
 * The minimum-non-zero count is floored at 0.15 of the colormap
 * range so a single-measurement cell is still clearly distinct from
 * the empty-cell background (otherwise it would render near-black
 * and be visually indistinguishable from "no data").
 */
export function colourForCount(count: number, max: number): string {
  if (count <= 0) return EMPTY_CELL_COLOUR;
  if (max <= 0) return EMPTY_CELL_COLOUR;
  const t = count / max;
  // Floor at 0.15 so single-measurement cells are visible.
  const tBoosted = Math.max(0.15, Math.min(1, t));
  const [r, g, b] = colormapRgb(tBoosted, "inferno");
  return `rgb(${Math.round(r * 255)}, ${Math.round(g * 255)}, ${Math.round(b * 255)})`;
}

/**
 * Vuetify-aligned neutral background for cells with zero measurements.
 * Chosen to match `grey-lighten-3` so the empty cells visually
 * recede behind the heat-mapped ones.
 */
export const EMPTY_CELL_COLOUR = "rgb(238, 238, 238)";

// ─── hasFailedMeasurement ─────────────────────────────────────────────────

/**
 * True when any DataObject in the cell carries a `NOK` qsClassification.
 *
 * Mirrors `cell.anyFailed` for use on cells that callers have built
 * by hand (e.g. unit tests). The component reads `cell.anyFailed`
 * directly for the production path.
 */
export function hasFailedMeasurement(cell: GridCellData): boolean {
  for (const m of cell.measurements) {
    if (m.qsClassification === "NOK") return true;
  }
  return false;
}

// ─── formatTooltip ────────────────────────────────────────────────────────

/**
 * Build the hover-tooltip string for a cell.
 *
 * Example: "Section S4 · Module M13 · 6 measurements · layers: L8, L18, L19"
 *
 * For a cell with NOK measurements, prepends a "FAILED:" marker.
 */
export function formatTooltip(cell: GridCellData): string {
  const count = cell.measurements.length;
  const noun = count === 1 ? "measurement" : "measurements";
  const layerStr =
    cell.layers.length === 0 ? "—" : cell.layers.join(", ");
  const head = `Section ${cell.section} · Module ${cell.module} · ${count} ${noun} · layers: ${layerStr}`;
  return cell.anyFailed ? `FAILED: ${head}` : head;
}

// ─── enumerateGrid ────────────────────────────────────────────────────────

/**
 * Produce the canonical 14x14 cell coordinates in row-major order:
 * (S1,M1), (S1,M2), ..., (S14,M14). Rendering iterates this list
 * and looks up the bucketed cell data by `cellKey()`.
 */
export function enumerateGrid(
  rows = 14,
  cols = 14,
): Array<{ section: string; module: string }> {
  const out: Array<{ section: string; module: string }> = [];
  for (let s = 1; s <= rows; s++) {
    for (let m = 1; m <= cols; m++) {
      out.push({ section: `S${s}`, module: `M${m}` });
    }
  }
  return out;
}

/**
 * Detect (cheaply) whether the supplied annotation list contains any
 * `urn:shepard:mffd:section` predicate. Used by the conditional
 * render: the parent page asks "does this collection have any
 * thermography data?" by checking a sample of DataObjects' annotations.
 */
export function annotationsContainSection(
  annotations: SemanticAnnotation[],
): boolean {
  for (const a of annotations) {
    if (a.propertyIRI === MFFD_SECTION_IRI) return true;
  }
  return false;
}

/**
 * Return the maximum measurement count across all cells in the
 * bucketed map. Used by the component to normalise the colour scale.
 * Returns 0 for an empty map.
 */
export function maxMeasurementCount(
  buckets: Map<string, GridCellData>,
): number {
  let max = 0;
  for (const cell of buckets.values()) {
    if (cell.measurements.length > max) max = cell.measurements.length;
  }
  return max;
}
