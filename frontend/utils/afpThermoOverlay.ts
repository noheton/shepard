/**
 * afpThermoOverlay.ts — pure helpers for the AfpThermoOverlayCanvas VIEW envelope.
 *
 * The VIEW envelope is produced by AfpThermoOverlayTransformExecutor (slice 2)
 * via {@code POST /v2/mappings/{templateAppId}/materialize}.
 *
 * Backlog: MFFD-RENDER-AFP-THERMO-OVERLAY slice 3.
 * Design: plugins/vis-afp-thermo-overlay/docs/reference.md.
 */

/** Tile-match verdict emitted by the TransformExecutor. */
export type TileMatch = "MATCHED" | "MISMATCHED" | "UNVERIFIED";

/** AFP section of the VIEW envelope. */
export interface AfpSection {
  dataObjectAppId: string;
  tcpTimeseriesRefAppId?: string;
  plyId?: string;
  courseId?: string;
  laserTempSetpointC?: number;
  tapeSpeedSetpointMpm?: number;
  materialBatchIri?: string;
  plyFilter?: number;
  courseFilter?: number;
  timeWindowStartUs?: number;
  timeWindowEndUs?: number;
}

/** NDT section of the VIEW envelope. */
export interface NdtSection {
  dataObjectAppId: string;
  section?: string;
  module?: string;
  layer?: string;
  frame?: string;
  sourceFileRefAppId?: string;
}

/** Full VIEW envelope emitted by AfpThermoOverlayTransformExecutor. */
export interface AfpThermoOverlayEnvelope {
  kind: "afp-thermo-overlay";
  syncMode: string;
  tcpChannel: string;
  colourMap: string;
  section?: string;
  module?: string;
  tileMatch: TileMatch;
  afp: AfpSection;
  ndt: NdtSection;
}

/** Vuetify colour name for a tileMatch verdict. */
export function tileMatchColor(match: TileMatch): string {
  switch (match) {
    case "MATCHED":    return "success";
    case "MISMATCHED": return "error";
    case "UNVERIFIED": return "warning";
  }
}

/** Human-readable label for a tileMatch verdict. */
export function tileMatchLabel(match: TileMatch): string {
  switch (match) {
    case "MATCHED":    return "Tile matched";
    case "MISMATCHED": return "Tile mismatch";
    case "UNVERIFIED": return "Not verified";
  }
}

/** MDI icon for a tileMatch verdict. */
export function tileMatchIcon(match: TileMatch): string {
  switch (match) {
    case "MATCHED":    return "mdi-check-circle-outline";
    case "MISMATCHED": return "mdi-alert-circle-outline";
    case "UNVERIFIED": return "mdi-help-circle-outline";
  }
}

/**
 * Format a temperature setpoint for display.
 * Returns a string like "220.0 °C" or "—" when absent.
 */
export function formatTempSetpoint(value: number | undefined | null): string {
  if (value == null) return "—";
  return `${value.toFixed(1)} °C`;
}

/**
 * Format a tape speed setpoint for display.
 * Returns a string like "50.0 m/min" or "—" when absent.
 */
export function formatSpeedSetpoint(value: number | undefined | null): string {
  if (value == null) return "—";
  return `${value.toFixed(1)} m/min`;
}

/**
 * Format a time window in microseconds for display.
 * Returns a string like "±1 200 000 µs" or an empty string when both bounds absent.
 */
export function formatTimeWindow(
  startUs: number | undefined | null,
  endUs: number | undefined | null,
): string {
  if (startUs == null && endUs == null) return "";
  const s = startUs != null ? `${startUs.toLocaleString()} µs` : "—";
  const e = endUs   != null ? `${endUs.toLocaleString()} µs`   : "—";
  return `${s} → ${e}`;
}

/**
 * Extract the last segment of an IRI for compact display.
 * E.g. "urn:shepard:mffd:material-batch:B2024-03" → "B2024-03".
 * Also handles fragment IRIs: "https://example.org/vocab/material#lot42" → "lot42".
 */
export function lastIriSegment(iri: string | undefined | null): string {
  if (!iri) return "—";
  return iri.split(/[:#/]/).pop() ?? iri;
}
