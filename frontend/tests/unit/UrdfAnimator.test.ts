/**
 * UrdfAnimator — unit tests for the pure interpolation + cursor helpers.
 *
 * Component is NOT mounted (matches the project's Vitest pattern, see
 * Trace3DChannelPicker.test.ts). All logic lives in `utils/urdfAnimation.ts`.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */
import { describe, it, expect } from "vitest";
import {
  type JointSample,
  type JointTrack,
  interpolateAt,
  jointValuesAt,
  trackTimeBounds,
  advanceCursor,
} from "~/utils/urdfAnimation";

// ── interpolateAt ─────────────────────────────────────────────────────────────

describe("interpolateAt", () => {
  const samples: JointSample[] = [
    { t: 0,    value: 0    },
    { t: 1000, value: 1    },
    { t: 2000, value: 0.5  },
    { t: 4000, value: 2    },
  ];

  it("returns null for an empty array", () => {
    expect(interpolateAt([], 100)).toBeNull();
  });

  it("returns the single value when length === 1", () => {
    expect(interpolateAt([{ t: 500, value: 7 }], 0)).toBe(7);
    expect(interpolateAt([{ t: 500, value: 7 }], 9999)).toBe(7);
  });

  it("clamps to first value when cursor is before first sample", () => {
    expect(interpolateAt(samples, -1000)).toBe(0);
  });

  it("clamps to last value when cursor is after last sample", () => {
    expect(interpolateAt(samples, 99_999)).toBe(2);
  });

  it("returns the exact value at an exact sample timestamp", () => {
    expect(interpolateAt(samples, 0)).toBe(0);
    expect(interpolateAt(samples, 1000)).toBe(1);
    expect(interpolateAt(samples, 2000)).toBe(0.5);
    expect(interpolateAt(samples, 4000)).toBe(2);
  });

  it("linearly interpolates between two samples — midpoint", () => {
    // Between t=0 (v=0) and t=1000 (v=1) at t=500 → 0.5
    expect(interpolateAt(samples, 500)).toBeCloseTo(0.5, 10);
  });

  it("linearly interpolates between non-equidistant samples", () => {
    // Between t=2000 (v=0.5) and t=4000 (v=2) at t=3000 → midpoint = 1.25
    expect(interpolateAt(samples, 3000)).toBeCloseTo(1.25, 10);
  });

  it("handles descending values correctly", () => {
    // Between t=1000 (v=1) and t=2000 (v=0.5) at t=1500 → 0.75
    expect(interpolateAt(samples, 1500)).toBeCloseTo(0.75, 10);
  });

  it("handles a zero-width segment defensively", () => {
    const flat: JointSample[] = [
      { t: 0, value: 1 },
      { t: 0, value: 9 },
      { t: 1, value: 2 },
    ];
    // Cursor at the duplicate-t point should not NaN
    const v = interpolateAt(flat, 0);
    expect(v).not.toBeNaN();
    expect(typeof v).toBe("number");
  });
});

// ── jointValuesAt ─────────────────────────────────────────────────────────────

describe("jointValuesAt", () => {
  it("returns an empty object for no tracks", () => {
    expect(jointValuesAt([], 0)).toEqual({});
  });

  it("returns a value per non-empty track", () => {
    const tracks: JointTrack[] = [
      { jointName: "shoulder", samples: [{ t: 0, value: 0.1 }, { t: 1, value: 0.3 }] },
      { jointName: "elbow",    samples: [{ t: 0, value: 1.0 }, { t: 1, value: 0.0 }] },
    ];
    const out = jointValuesAt(tracks, 0.5);
    expect(out.shoulder).toBeCloseTo(0.2, 10);
    expect(out.elbow).toBeCloseTo(0.5, 10);
  });

  it("skips tracks with empty samples", () => {
    const tracks: JointTrack[] = [
      { jointName: "shoulder", samples: [{ t: 0, value: 1 }] },
      { jointName: "elbow",    samples: [] },
    ];
    const out = jointValuesAt(tracks, 0);
    expect(out.shoulder).toBe(1);
    expect("elbow" in out).toBe(false);
  });
});

// ── trackTimeBounds ───────────────────────────────────────────────────────────

describe("trackTimeBounds", () => {
  it("returns null for an empty track list", () => {
    expect(trackTimeBounds([])).toBeNull();
  });

  it("returns null when every track is empty", () => {
    expect(trackTimeBounds([{ jointName: "j", samples: [] }])).toBeNull();
  });

  it("returns the min/max across all tracks", () => {
    const tracks: JointTrack[] = [
      { jointName: "a", samples: [{ t: 100, value: 0 }, { t: 500, value: 0 }] },
      { jointName: "b", samples: [{ t: 200, value: 0 }, { t: 900, value: 0 }] },
    ];
    expect(trackTimeBounds(tracks)).toEqual({ tMin: 100, tMax: 900 });
  });

  it("ignores empty tracks alongside populated ones", () => {
    const tracks: JointTrack[] = [
      { jointName: "a", samples: [] },
      { jointName: "b", samples: [{ t: 5, value: 0 }, { t: 10, value: 0 }] },
    ];
    expect(trackTimeBounds(tracks)).toEqual({ tMin: 5, tMax: 10 });
  });
});

// ── advanceCursor ─────────────────────────────────────────────────────────────

describe("advanceCursor", () => {
  const bounds = { tMin: 0, tMax: 1000 };

  it("advances the cursor forward at 1×", () => {
    const r = advanceCursor(100, 50, 1, bounds);
    expect(r.cursor).toBe(150);
    expect(r.finished).toBe(false);
  });

  it("advances the cursor backward when speed is negative", () => {
    const r = advanceCursor(500, 100, -1, bounds);
    expect(r.cursor).toBe(400);
    expect(r.finished).toBe(false);
  });

  it("clamps at tMax and reports finished=true", () => {
    const r = advanceCursor(950, 100, 1, bounds);
    expect(r.cursor).toBe(1000);
    expect(r.finished).toBe(true);
  });

  it("clamps at tMin and reports finished=true on reverse", () => {
    const r = advanceCursor(50, 100, -1, bounds);
    expect(r.cursor).toBe(0);
    expect(r.finished).toBe(true);
  });

  it("scales advance by the sampleTimeUnitsPerMs factor (ns samples)", () => {
    const nsBounds = { tMin: 0, tMax: 1_000_000_000 }; // 1 second worth of ns
    // 100 ms of wall time at 1× should advance 100 * 1e6 = 1e8 ns
    const r = advanceCursor(0, 100, 1, nsBounds, 1e6);
    expect(r.cursor).toBe(1e8);
    expect(r.finished).toBe(false);
  });
});
