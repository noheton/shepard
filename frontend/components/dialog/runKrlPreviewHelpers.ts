/**
 * KRL-INTERPRETER-06 — pure helpers extracted from RunKrlPreviewDialog.vue
 * so they can be unit-tested without mounting the full Nuxt / Vuetify component.
 *
 * Same pattern as EditFileReferenceDialog.test.ts and hdfReferences.test.ts.
 */
import type { KrlInterpretRequest } from "~/composables/useKrlInterpret";

/**
 * KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER — `timeseriesContainerAppId` is
 * now optional. When blank, the backend auto-mints the per-DataObject
 * "krl-default" container. The form is valid as long as a URDF file and a
 * target DataObject are provided.
 */
export function isKrlFormValid(state: {
  urdfFileAppId: string | null;
  targetDataObjectAppId: string;
}): boolean {
  return (
    !!state.urdfFileAppId &&
    state.urdfFileAppId.trim().length > 0 &&
    state.targetDataObjectAppId.trim().length > 0
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
  timeseriesContainerAppId: string;
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
    // omit timeseriesContainerAppId when blank — the backend auto-mints "krl-default"
    ...(input.timeseriesContainerAppId.trim()
      ? { timeseriesContainerAppId: input.timeseriesContainerAppId }
      : {}),
    timeStep: input.timeStep,
    options: {
      ikTolerance: input.ikTolerance,
      maxIterations: input.maxIterations,
    },
  };
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
