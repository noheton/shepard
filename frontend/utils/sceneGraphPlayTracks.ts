/**
 * Pure helpers for SCENEGRAPH-CANVAS-ANIM-1 — building JointTracks from a
 * SceneGraphPlay envelope's jointChannelBindings + timeseries bulk-data result.
 *
 * Extracted as pure functions so they are unit-testable without any Vue/DOM.
 *
 * Task: SCENEGRAPH-CANVAS-ANIM-1 (aidocs/16).
 */
import type { JointTrack } from "~/utils/urdfAnimation";

export interface JointChannelBinding {
  joint: string;
  channelSelector: string;
}

export interface ChannelTuple5 {
  measurement: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
}

export interface RoleChannelEntry {
  role: string;
  parsed: ChannelTuple5;
}

/**
 * Parse `jointChannelBindings` from the play envelope into the role+parsed
 * form expected by `fetchBulkTraceByAppId`. Each `channelSelector` is a
 * JSON-stringified 5-tuple `{measurement,device,location,symbolicName,field}`
 * written by the template authoring flow. Unparseable entries are silently
 * skipped so one bad binding does not block the rest.
 */
export function parseChannelBindings(
  bindings: JointChannelBinding[],
): RoleChannelEntry[] {
  const out: RoleChannelEntry[] = [];
  for (const b of bindings) {
    try {
      const parsed = JSON.parse(b.channelSelector) as Partial<ChannelTuple5>;
      if (
        parsed &&
        typeof parsed === "object" &&
        typeof parsed.measurement === "string" &&
        typeof parsed.device === "string" &&
        typeof parsed.location === "string" &&
        typeof parsed.symbolicName === "string" &&
        typeof parsed.field === "string"
      ) {
        out.push({
          role: b.joint,
          parsed: parsed as ChannelTuple5,
        });
      }
    } catch {
      // Selector is not valid JSON — skip.
    }
  }
  return out;
}

/**
 * Convert a `byRole` result from `fetchBulkTraceByAppId` into an array of
 * `JointTrack`s ready for `UrdfAnimator`.
 *
 * Timestamps arrive in nanoseconds from TimescaleDB; we normalise to
 * milliseconds so `UrdfAnimator`'s default `sampleTimeUnitsPerMs = 1`
 * gives correct 1× real-time playback speed and correct duration display.
 */
export function buildJointTracksFromByRole(
  byRole: Map<string, [number, number][]>,
): JointTrack[] {
  const tracks: JointTrack[] = [];
  for (const [jointName, points] of byRole.entries()) {
    if (points.length === 0) continue;
    tracks.push({
      jointName,
      samples: points.map(([tNs, value]) => ({ t: tNs / 1e6, value })),
    });
  }
  return tracks;
}
