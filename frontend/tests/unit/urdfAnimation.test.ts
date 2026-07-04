import { describe, expect, it } from "vitest";
import {
  advanceCursor,
  interpolateAt,
  jointValuesAt,
  trackTimeBounds,
  type JointSample,
  type JointTrack,
} from "~/utils/urdfAnimation";

// ── interpolateAt ────────────────────────────────────────────────────────────

describe("interpolateAt", () => {
  it("returns null for empty samples", () => {
    expect(interpolateAt([], 100)).toBeNull();
  });

  it("returns the single value for a single-sample track", () => {
    const s: JointSample[] = [{ t: 50, value: 1.5 }];
    expect(interpolateAt(s, 0)).toBe(1.5);
    expect(interpolateAt(s, 50)).toBe(1.5);
    expect(interpolateAt(s, 9999)).toBe(1.5);
  });

  it("clamps to first sample before the range", () => {
    const s: JointSample[] = [
      { t: 100, value: 2 },
      { t: 200, value: 4 },
    ];
    expect(interpolateAt(s, 0)).toBe(2);
    expect(interpolateAt(s, 99)).toBe(2);
  });

  it("clamps to last sample after the range", () => {
    const s: JointSample[] = [
      { t: 100, value: 2 },
      { t: 200, value: 4 },
    ];
    expect(interpolateAt(s, 200)).toBe(4);
    expect(interpolateAt(s, 999)).toBe(4);
  });

  it("returns exact value at a sample timestamp", () => {
    const s: JointSample[] = [
      { t: 0, value: 0 },
      { t: 100, value: 10 },
      { t: 200, value: 20 },
    ];
    expect(interpolateAt(s, 100)).toBe(10);
  });

  it("linearly interpolates between two samples", () => {
    const s: JointSample[] = [
      { t: 0, value: 0 },
      { t: 100, value: 10 },
    ];
    expect(interpolateAt(s, 50)).toBeCloseTo(5, 9);
    expect(interpolateAt(s, 25)).toBeCloseTo(2.5, 9);
    expect(interpolateAt(s, 75)).toBeCloseTo(7.5, 9);
  });

  it("correctly finds the bracket among many samples (binary search)", () => {
    const s: JointSample[] = Array.from({ length: 11 }, (_, i) => ({
      t: i * 10,
      value: i * 2,
    }));
    // Midpoints between each pair
    for (let i = 0; i < 10; i++) {
      const cursor = i * 10 + 5;
      const expected = i * 2 + 1;
      expect(interpolateAt(s, cursor)).toBeCloseTo(expected, 9);
    }
  });
});

// ── jointValuesAt ─────────────────────────────────────────────────────────────

describe("jointValuesAt", () => {
  it("returns empty map for empty tracks", () => {
    expect(jointValuesAt([], 100)).toEqual({});
  });

  it("maps joint name → interpolated value", () => {
    const tracks: JointTrack[] = [
      { jointName: "joint_a", samples: [{ t: 0, value: 1 }, { t: 100, value: 3 }] },
      { jointName: "joint_b", samples: [{ t: 0, value: 0 }, { t: 100, value: 2 }] },
    ];
    const out = jointValuesAt(tracks, 50);
    expect(out.joint_a).toBeCloseTo(2, 9);
    expect(out.joint_b).toBeCloseTo(1, 9);
  });

  it("omits tracks with no samples", () => {
    const tracks: JointTrack[] = [
      { jointName: "empty", samples: [] },
      { jointName: "filled", samples: [{ t: 0, value: 7 }] },
    ];
    const out = jointValuesAt(tracks, 0);
    expect("empty" in out).toBe(false);
    expect(out.filled).toBe(7);
  });
});

// ── trackTimeBounds ───────────────────────────────────────────────────────────

describe("trackTimeBounds", () => {
  it("returns null for empty track list", () => {
    expect(trackTimeBounds([])).toBeNull();
  });

  it("returns null when all tracks have empty samples", () => {
    const tracks: JointTrack[] = [
      { jointName: "a", samples: [] },
      { jointName: "b", samples: [] },
    ];
    expect(trackTimeBounds(tracks)).toBeNull();
  });

  it("returns correct bounds for a single track", () => {
    const tracks: JointTrack[] = [
      { jointName: "a", samples: [{ t: 10, value: 0 }, { t: 90, value: 1 }] },
    ];
    expect(trackTimeBounds(tracks)).toEqual({ tMin: 10, tMax: 90 });
  });

  it("unions bounds across multiple tracks", () => {
    const tracks: JointTrack[] = [
      { jointName: "a", samples: [{ t: 20, value: 0 }, { t: 60, value: 1 }] },
      { jointName: "b", samples: [{ t: 10, value: 0 }, { t: 80, value: 1 }] },
      { jointName: "c", samples: [] },
    ];
    expect(trackTimeBounds(tracks)).toEqual({ tMin: 10, tMax: 80 });
  });
});

// ── advanceCursor ─────────────────────────────────────────────────────────────

describe("advanceCursor", () => {
  const bounds = { tMin: 0, tMax: 1000 };

  it("advances cursor forward at 1× speed with ms samples", () => {
    const { cursor, finished } = advanceCursor(500, 10, 1, bounds, 1);
    expect(cursor).toBeCloseTo(510, 9);
    expect(finished).toBe(false);
  });

  it("clamps and signals finished at tMax on forward playback", () => {
    const { cursor, finished } = advanceCursor(990, 20, 1, bounds, 1);
    expect(cursor).toBe(1000);
    expect(finished).toBe(true);
  });

  it("advances in reverse at negative speed", () => {
    const { cursor, finished } = advanceCursor(500, 10, -1, bounds, 1);
    expect(cursor).toBeCloseTo(490, 9);
    expect(finished).toBe(false);
  });

  it("clamps and signals finished at tMin on reverse playback", () => {
    const { cursor, finished } = advanceCursor(5, 20, -1, bounds, 1);
    expect(cursor).toBe(0);
    expect(finished).toBe(true);
  });

  it("respects speed multiplier (2×)", () => {
    const { cursor } = advanceCursor(0, 100, 2, bounds, 1);
    expect(cursor).toBeCloseTo(200, 9);
  });

  it("converts nanosecond samples via sampleTimeUnitsPerMs=1e6", () => {
    const nsBounds = { tMin: 0, tMax: 1_000_000_000 }; // 1 s in ns
    // 10 ms wall-clock at 1× → 10 ms × 1e6 ns/ms = 10_000_000 ns
    const { cursor } = advanceCursor(0, 10, 1, nsBounds, 1e6);
    expect(cursor).toBeCloseTo(10_000_000, 0);
  });

  it("returns exact cursor when bounds are degenerate (tMin === tMax)", () => {
    const degen = { tMin: 500, tMax: 500 };
    const { cursor, finished } = advanceCursor(500, 10, 1, degen, 1);
    expect(cursor).toBe(500);
    expect(finished).toBe(true);
  });
});
