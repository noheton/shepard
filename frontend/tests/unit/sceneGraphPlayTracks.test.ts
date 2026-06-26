/**
 * Unit tests for sceneGraphPlayTracks — SCENEGRAPH-CANVAS-ANIM-1 pure helpers.
 *
 * `parseChannelBindings` and `buildJointTracksFromByRole` are extracted as
 * pure functions so they run in Vitest without Vue/DOM (matching the existing
 * UrdfAnimator.test.ts + Trace3DChannelPicker.test.ts pattern).
 */
import { describe, it, expect } from "vitest";
import {
  parseChannelBindings,
  buildJointTracksFromByRole,
  type JointChannelBinding,
} from "~/utils/sceneGraphPlayTracks";

// ── parseChannelBindings ───────────────────────────────────────────────────────

describe("parseChannelBindings", () => {
  const validBinding: JointChannelBinding = {
    joint: "joint_1",
    channelSelector: JSON.stringify({
      measurement: "afp_robot",
      device: "axis1",
      location: "augsburg",
      symbolicName: "joint_1_angle",
      field: "value",
    }),
  };

  it("parses a valid JSON 5-tuple binding", () => {
    const result = parseChannelBindings([validBinding]);
    expect(result).toHaveLength(1);
    expect(result[0]!.role).toBe("joint_1");
    expect(result[0]!.parsed.measurement).toBe("afp_robot");
    expect(result[0]!.parsed.field).toBe("value");
  });

  it("skips bindings with non-JSON channelSelector", () => {
    const bad: JointChannelBinding = { joint: "j", channelSelector: "not-json" };
    expect(parseChannelBindings([bad])).toHaveLength(0);
  });

  it("skips bindings missing required fields", () => {
    const incomplete: JointChannelBinding = {
      joint: "j",
      channelSelector: JSON.stringify({ measurement: "m" }),
    };
    expect(parseChannelBindings([incomplete])).toHaveLength(0);
  });

  it("parses only valid bindings from a mixed array", () => {
    const bad: JointChannelBinding = {
      joint: "bad",
      channelSelector: "{broken",
    };
    const result = parseChannelBindings([validBinding, bad]);
    expect(result).toHaveLength(1);
    expect(result[0]!.role).toBe("joint_1");
  });

  it("returns empty array for empty input", () => {
    expect(parseChannelBindings([])).toHaveLength(0);
  });
});

// ── buildJointTracksFromByRole ─────────────────────────────────────────────────

describe("buildJointTracksFromByRole", () => {
  it("converts ns timestamps to ms and preserves values", () => {
    const byRole = new Map<string, [number, number][]>([
      [
        "joint_1",
        [
          [1_000_000_000, 0.5],  // 1e9 ns = 1000 ms
          [2_000_000_000, 1.0],  // 2e9 ns = 2000 ms
        ],
      ],
    ]);
    const tracks = buildJointTracksFromByRole(byRole);
    expect(tracks).toHaveLength(1);
    expect(tracks[0]!.jointName).toBe("joint_1");
    expect(tracks[0]!.samples[0]!.t).toBeCloseTo(1000);
    expect(tracks[0]!.samples[0]!.value).toBe(0.5);
    expect(tracks[0]!.samples[1]!.t).toBeCloseTo(2000);
    expect(tracks[0]!.samples[1]!.value).toBe(1.0);
  });

  it("skips joints with no data points", () => {
    const byRole = new Map<string, [number, number][]>([
      ["joint_1", []],
      ["joint_2", [[500_000_000, 0.1]]],
    ]);
    const tracks = buildJointTracksFromByRole(byRole);
    expect(tracks).toHaveLength(1);
    expect(tracks[0]!.jointName).toBe("joint_2");
  });

  it("returns empty array from empty map", () => {
    expect(buildJointTracksFromByRole(new Map())).toHaveLength(0);
  });

  it("produces monotonic ms timestamps from monotonic ns input", () => {
    // 30s × 100 Hz = 3000 points (simplified to 4 here)
    const nsBase = 1_700_000_000_000_000_000; // ~2023-11-14 in ns
    const nsStep = 10_000_000; // 10 ms in ns
    const rawPoints: [number, number][] = [
      [nsBase, 0.0],
      [nsBase + nsStep, 0.1],
      [nsBase + 2 * nsStep, 0.2],
      [nsBase + 3 * nsStep, 0.3],
    ];
    const tracks = buildJointTracksFromByRole(
      new Map([["joint_1", rawPoints]]),
    );
    const samples = tracks[0]!.samples;
    expect(samples).toHaveLength(4);
    // Each step should be 10 ms in the converted output.
    const step0 = samples[1]!.t - samples[0]!.t;
    const step1 = samples[2]!.t - samples[1]!.t;
    expect(step0).toBeCloseTo(10);
    expect(step1).toBeCloseTo(10);
  });
});
