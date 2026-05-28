/**
 * Pure helpers for UrdfChannelPicker — channel ↔ joint binding logic.
 *
 * Mirrors the Trace3DChannelPicker tupleKey + channelFor pattern but binds
 * one channel per URDF movable joint instead of one channel per spatial axis.
 *
 * Annotation-driven preselection (per `project_annotation_preselection_principle.md`):
 *   A channel annotated with predicate `urn:shepard:urdf:joint = <jointName>`
 *   auto-binds to that joint. Heuristic fallback: a channel whose symbolicName
 *   or field equals the joint name (case-insensitive) auto-binds.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */

export interface UrdfPickerChannel {
  shepardId: string;
  measurement?: string;
  device?: string;
  location?: string;
  symbolicName?: string;
  field?: string;
  /**
   * The joint name this channel is currently annotated to drive, if any.
   * Discovered from a `urn:shepard:urdf:joint` annotation on the channel
   * (read by the picker on open). Empty/undefined means no annotation.
   */
  annotatedJoint?: string;
}

export interface UrdfPickerJoint {
  /** Joint name as declared in the URDF. */
  name: string;
  /** "revolute" | "prismatic" | "continuous" | "fixed" | … */
  jointType: string;
}

export type UrdfJointBinding = Record<string, string | null>; // jointName → channel shepardId (or null)

/** Stable string key for a channel — used as v-select item value. */
export const URDF_JOINT_PREDICATE = "urn:shepard:urdf:joint";

const norm = (s: string | null | undefined) => (s ?? "").trim().toLowerCase();

/**
 * Decide which channel should preselect for a joint, applying (in order):
 *   1. Annotation match — a channel with `annotatedJoint === jointName`.
 *   2. Heuristic match — symbolicName or field equals jointName (case-insensitive).
 *   3. None — caller leaves the slot blank.
 *
 * Returns the channel's shepardId, or null if no match.
 */
export function preselectChannelForJoint(
  jointName: string,
  channels: UrdfPickerChannel[],
): string | null {
  if (!jointName) return null;

  // Pass 1 — annotation match
  const annotated = channels.find(c => c.annotatedJoint && c.annotatedJoint === jointName);
  if (annotated) return annotated.shepardId;

  // Pass 2 — heuristic match
  const target = norm(jointName);
  const heuristic = channels.find(
    c => norm(c.symbolicName) === target || norm(c.field) === target,
  );
  if (heuristic) return heuristic.shepardId;

  return null;
}

/**
 * Initial binding map: one entry per movable joint, filled by preselect logic.
 * Fixed joints are skipped.
 */
export function initialBinding(
  joints: UrdfPickerJoint[],
  channels: UrdfPickerChannel[],
): UrdfJointBinding {
  const out: UrdfJointBinding = {};
  for (const j of joints) {
    if (j.jointType === "fixed") continue;
    out[j.name] = preselectChannelForJoint(j.name, channels);
  }
  return out;
}

/** A binding is "ready" once at least one joint has a channel assigned. */
export function isBindingReady(binding: UrdfJointBinding): boolean {
  return Object.values(binding).some(v => v !== null && v !== "");
}

/** Inverse lookup — given a binding + the full channel list, build a name → channel map. */
export function resolveBoundChannels(
  binding: UrdfJointBinding,
  channels: UrdfPickerChannel[],
): Record<string, UrdfPickerChannel> {
  const byId = new Map<string, UrdfPickerChannel>();
  for (const c of channels) byId.set(c.shepardId, c);
  const out: Record<string, UrdfPickerChannel> = {};
  for (const [jointName, id] of Object.entries(binding)) {
    if (!id) continue;
    const ch = byId.get(id);
    if (ch) out[jointName] = ch;
  }
  return out;
}

// ─── joint-spec extraction (UrdfCanvas → UrdfJointPanel handoff) ─────────────

export interface UrdfJointLike {
  name?: string;
  jointType?: string;
  limit?: { lower?: number; upper?: number } | null;
}

export interface UrdfRobotLike {
  joints: Record<string, UrdfJointLike>;
}

/**
 * Translate a loaded URDFRobot's `joints` map into the lightweight
 * {@link UrdfJointSpec}-compatible records consumed by UrdfJointPanel /
 * UrdfChannelPicker. Fixed joints are filtered out by default.
 */
export function extractJointSpecs(
  robot: UrdfRobotLike | null | undefined,
  { includeFixed = false }: { includeFixed?: boolean } = {},
): { name: string; jointType: string; lower: number | null; upper: number | null }[] {
  if (!robot || !robot.joints) return [];
  const out: { name: string; jointType: string; lower: number | null; upper: number | null }[] = [];
  for (const [name, j] of Object.entries(robot.joints)) {
    const jt = j.jointType ?? "revolute";
    if (!includeFixed && jt === "fixed") continue;
    out.push({
      name:      j.name ?? name,
      jointType: jt,
      lower:     j.limit?.lower ?? null,
      upper:     j.limit?.upper ?? null,
    });
  }
  return out;
}
