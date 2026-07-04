/**
 * Pure helpers for the (tier-1) ThermographyChannelPicker.
 *
 * Tier-1 reality: there is no per-channel binding yet — frame extraction
 * (sequence0/sequence1 → IR texture stream) is filed as OTVIS-PARSE-2,
 * and channel-bound playback is filed as THERMO-CHANNELS-1. So in tier-1
 * this module owns the metadata-summary projection rather than a channel
 * binding map: given the annotations that the OTvis parser emitted on
 * the parent DataObject + FileReference, build the human-readable
 * summary panel rows that the picker component renders.
 *
 * Annotation-driven preselection (per
 * `project_annotation_preselection_principle.md`): when tier-2 lands and
 * we acquire actual timeseries channels (e.g. mean temperature per
 * frame, lock-in amplitude per pixel-region), the channels will carry
 * `urn:shepard:thermography:role = <role>` annotations that this module
 * will turn into preselected slot bindings — exactly the URDF
 * `preselectChannelForJoint` shape. Until then this is metadata-only.
 *
 * Task: OTVIS-VIEW-1 (aidocs/16). Companion: aidocs/integrations/114.
 */

// ─── canonical predicates emitted by the OTvis tier-1 parser ─────────────

/** {@code urn:shepard:thermography:*} acquisition predicates. */
export const THERMOGRAPHY_PREDICATES = {
  frameRateHz:             "urn:shepard:thermography:frameRate_Hz",
  integrationTimeS:        "urn:shepard:thermography:integrationTime_s",
  excitationDevice:        "urn:shepard:thermography:excitationDevice",
  excitationFrequencyHz:   "urn:shepard:thermography:excitationFrequency_Hz",
  excitationAmplitudePct:  "urn:shepard:thermography:excitationAmplitude_pct",
  excitationSignalType:    "urn:shepard:thermography:excitationSignalType",
  recordingType:           "urn:shepard:thermography:recordingType",
  resolution:              "urn:shepard:thermography:resolution",
  conditioningPeriods:     "urn:shepard:thermography:conditioningPeriods",
  acquisitionPeriods:      "urn:shepard:thermography:acquisitionPeriods",
  campaign:                "urn:shepard:thermography:campaign",
  moduleName:              "urn:shepard:thermography:moduleName",
  creatingVersion:         "urn:shepard:thermography:creatingVersion",
  createdAt:               "urn:shepard:thermography:createdAt",
} as const;

/** {@code urn:shepard:mffd:*} grid-position predicates. */
export const MFFD_GRID_PREDICATES = {
  section: "urn:shepard:mffd:section",
  module:  "urn:shepard:mffd:module",
  layer:   "urn:shepard:mffd:layer",
  frame:   "urn:shepard:mffd:frame",
} as const;

// ─── shapes ──────────────────────────────────────────────────────────────

/**
 * Map of predicate IRI → string value. The picker accepts a flattened
 * predicate-to-value map rather than the full SemanticAnnotation list
 * shape so that callers using either v1 attributes or v2 annotations can
 * normalise to the same input.
 */
export type AnnotationMap = Record<string, string | undefined>;

export interface MetadataRow {
  /** Friendly label shown in the table. */
  label: string;
  /** Display value — may include unit suffix; "—" when unknown. */
  value: string;
  /** Section grouping for the rendered panel. */
  group: "grid" | "acquisition" | "excitation" | "provenance";
  /** Source predicate IRI (debug + advanced-mode display). */
  predicate: string;
}

// ─── grid-position projection ────────────────────────────────────────────

export interface GridPosition {
  section: string | null;
  module:  string | null;
  layer:   string | null;
  frame:   string | null;
}

/**
 * Extract the four-component MFFD grid position from an annotation map.
 * Returns null fields for missing predicates — callers decide whether to
 * hide the section.
 */
export function extractGridPosition(annotations: AnnotationMap): GridPosition {
  return {
    section: annotations[MFFD_GRID_PREDICATES.section] ?? null,
    module:  annotations[MFFD_GRID_PREDICATES.module]  ?? null,
    layer:   annotations[MFFD_GRID_PREDICATES.layer]   ?? null,
    frame:   annotations[MFFD_GRID_PREDICATES.frame]   ?? null,
  };
}

/** {@code "S4 · M13 · L18 · F4"} or {@code "—"} when nothing is set. */
export function formatGridPosition(g: GridPosition): string {
  const parts = [g.section, g.module, g.layer, g.frame].filter(Boolean);
  return parts.length > 0 ? parts.join(" · ") : "—";
}

/** True iff at least one MFFD grid predicate is present. */
export function hasGridPosition(annotations: AnnotationMap): boolean {
  return Object.values(MFFD_GRID_PREDICATES).some(p => Boolean(annotations[p]));
}

// ─── metadata-row projection ─────────────────────────────────────────────

const FIELD_DEFINITIONS: { label: string; predicate: string; group: MetadataRow["group"]; unit?: string }[] = [
  // grid
  { label: "Section",            predicate: MFFD_GRID_PREDICATES.section,                       group: "grid" },
  { label: "Module",             predicate: MFFD_GRID_PREDICATES.module,                        group: "grid" },
  { label: "Layer",              predicate: MFFD_GRID_PREDICATES.layer,                         group: "grid" },
  { label: "Frame",              predicate: MFFD_GRID_PREDICATES.frame,                         group: "grid" },
  // acquisition
  { label: "Resolution",         predicate: THERMOGRAPHY_PREDICATES.resolution,                 group: "acquisition" },
  { label: "Frame rate",         predicate: THERMOGRAPHY_PREDICATES.frameRateHz,                group: "acquisition", unit: "Hz" },
  { label: "Integration time",   predicate: THERMOGRAPHY_PREDICATES.integrationTimeS,           group: "acquisition", unit: "s" },
  { label: "Recording type",     predicate: THERMOGRAPHY_PREDICATES.recordingType,              group: "acquisition" },
  // excitation
  { label: "Excitation device",     predicate: THERMOGRAPHY_PREDICATES.excitationDevice,        group: "excitation" },
  { label: "Excitation signal",     predicate: THERMOGRAPHY_PREDICATES.excitationSignalType,    group: "excitation" },
  { label: "Excitation frequency",  predicate: THERMOGRAPHY_PREDICATES.excitationFrequencyHz,   group: "excitation", unit: "Hz" },
  { label: "Excitation amplitude",  predicate: THERMOGRAPHY_PREDICATES.excitationAmplitudePct,  group: "excitation", unit: "%" },
  { label: "Conditioning periods",  predicate: THERMOGRAPHY_PREDICATES.conditioningPeriods,     group: "excitation" },
  { label: "Acquisition periods",   predicate: THERMOGRAPHY_PREDICATES.acquisitionPeriods,      group: "excitation" },
  // provenance
  { label: "Campaign",           predicate: THERMOGRAPHY_PREDICATES.campaign,                   group: "provenance" },
  { label: "Module",             predicate: THERMOGRAPHY_PREDICATES.moduleName,                 group: "provenance" },
  { label: "Software version",   predicate: THERMOGRAPHY_PREDICATES.creatingVersion,            group: "provenance" },
  { label: "Created at",         predicate: THERMOGRAPHY_PREDICATES.createdAt,                  group: "provenance" },
];

/**
 * Project an annotation map into the row list the picker renders. Rows
 * for predicates with no annotation value are omitted (rather than shown
 * with "—") so the panel is dense and useful — a missing predicate
 * conveys no information.
 */
export function projectMetadataRows(annotations: AnnotationMap): MetadataRow[] {
  const out: MetadataRow[] = [];
  for (const def of FIELD_DEFINITIONS) {
    const raw = annotations[def.predicate];
    if (raw === undefined || raw === null || raw === "") continue;
    const value = def.unit ? `${raw} ${def.unit}` : raw;
    out.push({ label: def.label, value, group: def.group, predicate: def.predicate });
  }
  return out;
}

/**
 * Parse a "WxH" resolution annotation (e.g. "1024x768") to an aspect ratio
 * (W/H). Returns the Edevis OTvis default 1024/768 when the string is absent
 * or cannot be parsed. Accepts × (U+00D7) as well as ASCII x/X.
 */
export function parseAspectRatio(resolution: string | null | undefined): number {
  if (!resolution) return 1024 / 768;
  const m = /^(\d+)[xX×](\d+)$/.exec(resolution.trim());
  if (!m) return 1024 / 768;
  const w = Number(m[1]), h = Number(m[2]);
  return h > 0 ? w / h : 1024 / 768;
}

/** Group rows by section for the rendered panel. */
export function groupMetadataRows(rows: MetadataRow[]): Record<MetadataRow["group"], MetadataRow[]> {
  const out: Record<MetadataRow["group"], MetadataRow[]> = {
    grid: [],
    acquisition: [],
    excitation: [],
    provenance: [],
  };
  for (const r of rows) {
    out[r.group].push(r);
  }
  return out;
}
