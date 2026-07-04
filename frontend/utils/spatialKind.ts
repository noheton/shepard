/**
 * spatialKind — heuristic classifier mapping a SpatialDataReference name
 * (which mirrors the source filename until the dedicated ``kind`` field
 * ships on SpatialDataReferenceIO) to a render-mode hint.
 *
 * MFFD W7 / GAP-5.
 */

export type SpatialKind = "profile" | "trajectory" | "brush-trace" | "other";

/**
 * Infer the SpatialDataContainer kind from its name. Names produced by the
 * Python importer take the form ``Track_NN__Run_NN_/<source-filename>``, so
 * checking suffixes is enough to disambiguate.
 */
export function inferSpatialKindFromName(name: string | null | undefined): SpatialKind {
  const lower = (name ?? "").toLowerCase();
  if (lower.includes("tps 3d pointclouds")) return "profile";
  if (lower.includes("fsd course 3d pointclouds")) return "trajectory";
  if (lower.includes("tps raw data")) return "brush-trace";
  return "other";
}

/** Map a kind to a user-facing label. */
export const SPATIAL_KIND_LABELS: Record<SpatialKind, string> = {
  profile: "Surface profile (laser scan)",
  trajectory: "TCP trajectory",
  "brush-trace": "Brush trace (temporal sweep)",
  other: "Other spatial data",
};

/** Map a kind to a Material Design Icon name. */
export const SPATIAL_KIND_ICONS: Record<SpatialKind, string> = {
  profile: "mdi-grid",
  trajectory: "mdi-vector-polyline",
  "brush-trace": "mdi-paint-outline",
  other: "mdi-map-marker-outline",
};
