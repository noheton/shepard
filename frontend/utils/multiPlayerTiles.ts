/**
 * MFFD-MULTIPLAYER-1 — helper to decide which tiles the synchronised
 * multi-payload player should render for a DataObject.
 *
 * <p>Centralises the per-payload-kind detection in one place so the
 * DataObject detail page stays thin and the unit tests can pin the
 * "hide pane when < 2 payload kinds" rule without spinning up the full
 * page composables.
 *
 * <p>The detection mirrors the heuristics already used by the DataObject
 * detail page (e.g. thermography by filename hint, video via the
 * VideoStreamReference list) so the multi-player surfaces exactly when
 * the per-kind tabs would also render.
 */

import type { MultiPlayerTileKind } from "~/components/context/multiplayer/MultiPlayerPane.vue";

export interface MultiPlayerTileInputs {
  /** Whether at least one TimeseriesReference is attached. */
  hasTimeseries: boolean;
  /** Whether at least one VideoStreamReference is attached. */
  hasVideo: boolean;
  /** appId of a thermography-shaped FileBundleReference, or null. */
  thermographyBundleAppId: string | null;
  /** Whether at least one SpatialDataContainer is attached. */
  hasSpatial: boolean;
}

/** Hardcoded v1 tile order. {@code MFFD-MULTIPLAYER-CONFIG-1} will lift this. */
export const DEFAULT_TILE_ORDER: ReadonlyArray<MultiPlayerTileKind> = [
  "ts",
  "video",
  "thermo",
  "spatial",
] as const;

/**
 * Compute the tile list to render. Returns an empty array when fewer than
 * 2 distinct payload kinds are present — the caller hides the panel in
 * that case (the "no point in a multi with one tile" rule from the brief).
 */
export function selectMultiPlayerTiles(
  inputs: MultiPlayerTileInputs,
): MultiPlayerTileKind[] {
  const candidates: MultiPlayerTileKind[] = [];
  for (const kind of DEFAULT_TILE_ORDER) {
    if (kind === "ts" && inputs.hasTimeseries) candidates.push("ts");
    else if (kind === "video" && inputs.hasVideo) candidates.push("video");
    else if (kind === "thermo" && inputs.thermographyBundleAppId)
      candidates.push("thermo");
    else if (kind === "spatial" && inputs.hasSpatial) candidates.push("spatial");
  }
  if (candidates.length < 2) return [];
  return candidates;
}
