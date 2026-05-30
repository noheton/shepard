/**
 * KRL-INTERPRETER-06 — pure helpers extracted from RunKrlPreviewDialog.vue
 * so they can be unit-tested without mounting the full Nuxt / Vuetify component.
 *
 * Same pattern as EditFileReferenceDialog.test.ts and hdfReferences.test.ts.
 */
import type { KrlInterpretRequest } from "~/composables/useKrlInterpret";

// KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER: timeseriesContainerAppId is now
// optional — when absent the backend auto-mints "KRL Trajectories" for the
// target DataObject.
export function isKrlFormValid(state: {
  urdfFileAppId: string | null;
  targetDataObjectAppId: string;
  timeseriesContainerAppId?: string | null;
}): boolean {
  return (
    !!state.urdfFileAppId &&
    state.urdfFileAppId.trim().length > 0 &&
    state.targetDataObjectAppId.trim().length > 0
    // timeseriesContainerAppId is optional; no gate here
  );
}

export function parseSeedPose(raw: string): number[] | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const parts = trimmed
    .split(/[,\s]+/)
    .map(p => p.trim())
    .filter(Boolean);
  const nums = parts.map(Number);
  if (nums.some(n => Number.isNaN(n))) return undefined;
  return nums;
}

export interface BuildBodyInput {
  srcFileAppId: string;
  urdfFileAppId: string;
  targetDataObjectAppId: string;
  /** Optional — when blank/null the backend auto-mints "KRL Trajectories". */
  timeseriesContainerAppId?: string | null;
  datFileAppIds: string[];
  timeStep: number;
  ikTolerance: number;
  maxIterations: number;
  useBaseFrame: boolean;
  useToolFrame: boolean;
  baseFrame: { x: number; y: number; z: number; rx: number; ry: number; rz: number };
  toolFrame: { x: number; y: number; z: number; rx: number; ry: number; rz: number };
  seedPoseRaw: string;
}

export function buildKrlRequestBody(input: BuildBodyInput): KrlInterpretRequest {
  const body: KrlInterpretRequest = {
    srcFileAppId: input.srcFileAppId,
    urdfFileAppId: input.urdfFileAppId,
    targetDataObjectAppId: input.targetDataObjectAppId,
    timeStep: input.timeStep,
    options: {
      ikTolerance: input.ikTolerance,
      maxIterations: input.maxIterations,
    },
  };
  // Only include timeseriesContainerAppId when the caller supplied a non-blank
  // value — absent means "auto-mint KRL Trajectories" on the backend.
  if (input.timeseriesContainerAppId && input.timeseriesContainerAppId.trim().length > 0) {
    body.timeseriesContainerAppId = input.timeseriesContainerAppId;
  }
  if (input.datFileAppIds.length > 0) {
    body.datFileAppIds = [...input.datFileAppIds];
  }
  if (input.useBaseFrame) {
    body.baseFrame = { ...input.baseFrame };
  }
  if (input.useToolFrame) {
    body.toolFrame = { ...input.toolFrame };
  }
  const seed = parseSeedPose(input.seedPoseRaw);
  if (seed !== undefined) body.seedPose = seed;
  return body;
}

/**
 * Pure filter: extract URDF FileReferences from a candidate list, mapping
 * to the {title, value} shape v-autocomplete expects.
 */
export function urdfPickerOptions(
  refs: Array<{ name: string; appId?: string | null }>,
): Array<{ title: string; value: string }> {
  return refs
    .filter(r => r.name.toLowerCase().endsWith(".urdf"))
    .map(r => ({ title: r.name, value: r.appId ?? "" }))
    .filter(o => o.value !== "");
}

export function datPickerOptions(
  refs: Array<{ name: string; appId?: string | null }>,
): Array<{ title: string; value: string }> {
  return refs
    .filter(r => r.name.toLowerCase().endsWith(".dat"))
    .map(r => ({ title: r.name, value: r.appId ?? "" }))
    .filter(o => o.value !== "");
}

/**
 * Find the same-stem .dat companion for a given .src file name.
 * Returns the matched candidate's appId or null.
 */
export function sameStemDatAppId(
  srcFileName: string,
  datOptions: Array<{ title: string; value: string }>,
): string | null {
  const stem = srcFileName.replace(/\.[^.]+$/, "");
  if (!stem) return null;
  const match = datOptions.find(d => d.title.startsWith(stem));
  return match ? match.value : null;
}
