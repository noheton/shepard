/**
 * Pure helpers for URDF joint-trajectory playback (UrdfAnimator).
 *
 * Extracted as plain functions so they can be unit-tested without mounting
 * the Vue component (matching the project's Vitest pattern — see
 * `frontend/tests/unit/Trace3DChannelPicker.test.ts`).
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16). Design: aidocs/integrations/113-urdf-viewer.md.
 */

export interface JointSample {
  /** Timestamp — units are caller-defined (ns, ms, s). Must be monotonic ascending. */
  t: number;
  /** Joint value at t. Units: radians for revolute, metres for prismatic. */
  value: number;
}

export interface JointTrack {
  /** Joint name as declared in the URDF (URDFRobot.joints[name]). */
  jointName: string;
  /** Time-ordered samples for this joint. */
  samples: JointSample[];
}

/**
 * Linear interpolation of a sorted (by t) `samples` array at the given cursor.
 *
 * - Returns null for an empty samples array.
 * - Cursor before first sample → returns samples[0].value (clamp).
 * - Cursor at/after last sample → returns samples[last].value (clamp).
 * - Cursor at an exact sample timestamp → returns that sample's value.
 * - Between two samples → linear interpolation.
 */
export function interpolateAt(samples: JointSample[], cursor: number): number | null {
  if (samples.length === 0) return null;
  if (samples.length === 1) return samples[0]!.value;

  // Clamp to endpoints
  if (cursor <= samples[0]!.t)                 return samples[0]!.value;
  if (cursor >= samples[samples.length - 1]!.t) return samples[samples.length - 1]!.value;

  // Binary search for the bracketing pair [lo, hi] such that samples[lo].t <= cursor < samples[hi].t
  let lo = 0, hi = samples.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >>> 1;
    if (samples[mid]!.t <= cursor) lo = mid;
    else                            hi = mid;
  }

  const a = samples[lo]!;
  const b = samples[hi]!;
  if (b.t === a.t) return a.value; // defensive: zero-width segment
  const frac = (cursor - a.t) / (b.t - a.t);
  return a.value + (b.value - a.value) * frac;
}

/**
 * Resolve a set of joint tracks at a cursor → joint name → value map suitable
 * for passing to UrdfCanvas's `jointValues` prop or robot.setJointValue().
 *
 * Tracks with no samples (or that interpolate to null) are silently dropped.
 */
export function jointValuesAt(tracks: JointTrack[], cursor: number): Record<string, number> {
  const out: Record<string, number> = {};
  for (const track of tracks) {
    const v = interpolateAt(track.samples, cursor);
    if (v !== null) out[track.jointName] = v;
  }
  return out;
}

/**
 * The aggregate [tMin, tMax] over all tracks, or null if no track has samples.
 * Used by UrdfAnimator to size the scrub slider and to clamp the cursor.
 */
export function trackTimeBounds(tracks: JointTrack[]): { tMin: number; tMax: number } | null {
  let tMin = Infinity;
  let tMax = -Infinity;
  for (const track of tracks) {
    if (track.samples.length === 0) continue;
    const first = track.samples[0]!.t;
    const last  = track.samples[track.samples.length - 1]!.t;
    if (first < tMin) tMin = first;
    if (last  > tMax) tMax = last;
  }
  if (!isFinite(tMin) || !isFinite(tMax)) return null;
  return { tMin, tMax };
}

/**
 * Advance a cursor by `dtMs` of wall-clock time at `speed` × playback rate.
 *
 * Returns the new cursor and whether playback finished (cursor reached tMax).
 * Supports negative speed for reverse playback (clamps at tMin).
 */
export function advanceCursor(
  cursor: number,
  dtMs: number,
  speed: number,
  bounds: { tMin: number; tMax: number },
  /** Conversion factor from sample-time units to milliseconds (e.g. 1 for ms, 1e-6 for ns). */
  sampleTimeUnitsPerMs = 1,
): { cursor: number; finished: boolean } {
  const advance = dtMs * speed * sampleTimeUnitsPerMs;
  let next = cursor + advance;
  let finished = false;
  if (next >= bounds.tMax) { next = bounds.tMax; finished = true; }
  if (next <= bounds.tMin) { next = bounds.tMin; finished = true; }
  return { cursor: next, finished };
}
