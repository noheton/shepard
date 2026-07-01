/**
 * Pure helpers for the SvdxChannelChartView component.
 *
 * Parses the `channelSelector` JSON emitted by SvdxChannelChartRenderer and
 * provides grouping / manifest-extraction utilities that the Vue component can
 * use directly, and that Vitest can exercise without mounting Vue.
 *
 * Backlog: SVDX-CHANNEL-CHART-VUE-2026-06-29 (aidocs/16-dispatcher-backlog.md).
 */

export interface SvdxManifestInfo {
  channelCount?: string;
  acquisitionCount?: string;
  projectName?: string;
  dataTypes?: string;
  amsNetIds?: string;
  ports?: string;
}

/** JSON shape of `channelSelector` as emitted by SvdxChannelChartRenderer. */
export interface SvdxChannelSelector {
  /** Present for channel-N roles; absent for acquisition-N and MISSING rows. */
  channelName?: string;
  /** Present for acquisition-N roles; absent for channel-N rows. */
  symbolName?: string;
  /** TwinCAT data type, e.g. "REAL32", "INT16". File-level, same for all channels in one .svdx. */
  dataType?: string;
  /** AMS Net ID of the PLC, e.g. "1.2.3.4.1.1". File-level. */
  amsNetId?: string;
  /** ADS port, e.g. "851". File-level. */
  port?: string;
  /** File-level summary present on every binding. */
  manifest?: SvdxManifestInfo;
  /** Only present on MISSING bindings — appId of the entity that was probed. */
  anchorAppId?: string;
  /** Only present on MISSING bindings — human-readable reason. */
  reason?: string;
}

/** Parsed representation of one binding row, ready for the Vue template. */
export interface SvdxBindingRow {
  role: string;
  status: string;
  /** null when channelSelector is absent or unparseable (MISSING rows may still have JSON). */
  selector: SvdxChannelSelector | null;
}

/** Try to parse a raw channelSelector string; returns null on any failure. */
export function parseSvdxSelector(
  channelSelector: string | null | undefined,
): SvdxChannelSelector | null {
  if (!channelSelector) return null;
  try {
    return JSON.parse(channelSelector) as SvdxChannelSelector;
  } catch {
    return null;
  }
}

/**
 * Group binding rows by their `dataType` field (the default `svdx-ui:groupBy` knob).
 * Rows with no dataType land under the key `"(unknown)"`.
 */
export function groupSvdxBindingsByDataType(
  rows: SvdxBindingRow[],
): Map<string, SvdxBindingRow[]> {
  const map = new Map<string, SvdxBindingRow[]>();
  for (const row of rows) {
    const key = row.selector?.dataType ?? "(unknown)";
    const bucket = map.get(key) ?? [];
    bucket.push(row);
    map.set(key, bucket);
  }
  return map;
}

/**
 * Extract the shared manifest metadata from the first binding that carries one.
 * Returns null when none of the rows has a manifest (e.g. all MISSING).
 */
export function extractManifest(rows: SvdxBindingRow[]): SvdxManifestInfo | null {
  for (const row of rows) {
    if (row.selector?.manifest) return row.selector.manifest;
  }
  return null;
}
