/**
 * UrdfCanvas — unit tests for the URDFRobot → JointSpec extraction helper.
 *
 * The Vue component itself wraps Three.js + urdf-loader and is impractical
 * to mount in a node test environment (Vitest config uses `environment:
 * "node"`). Instead we test the pure helper that translates a loaded
 * URDFRobot's joint map into the lightweight specs UrdfJointPanel and
 * UrdfChannelPicker consume. This is the load-bearing handoff between
 * UrdfCanvas's `@robot-loaded` event and downstream UI.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */
import { describe, it, expect } from "vitest";
import { extractJointSpecs, type UrdfRobotLike } from "~/utils/urdfChannelPicker";

// ── fixtures ──────────────────────────────────────────────────────────────────

const TWO_LINK_ROBOT: UrdfRobotLike = {
  joints: {
    base:     { name: "base",     jointType: "fixed",    limit: null },
    shoulder: { name: "shoulder", jointType: "revolute", limit: { lower: -3.14159, upper: 3.14159 } },
    elbow:    { name: "elbow",    jointType: "revolute", limit: { lower: -2.5,     upper: 2.5     } },
    slider:   { name: "slider",   jointType: "prismatic", limit: { lower: 0, upper: 0.5 } },
    rotator:  { name: "rotator",  jointType: "continuous" },
  },
};

// ── extractJointSpecs ─────────────────────────────────────────────────────────

describe("extractJointSpecs", () => {
  it("returns [] for a null robot", () => {
    expect(extractJointSpecs(null)).toEqual([]);
  });

  it("returns [] for an undefined robot", () => {
    expect(extractJointSpecs(undefined)).toEqual([]);
  });

  it("returns [] for a robot with no joints", () => {
    expect(extractJointSpecs({ joints: {} })).toEqual([]);
  });

  it("skips fixed joints by default", () => {
    const specs = extractJointSpecs(TWO_LINK_ROBOT);
    expect(specs.map(s => s.name).sort()).toEqual(["elbow", "rotator", "shoulder", "slider"]);
    expect(specs.find(s => s.name === "base")).toBeUndefined();
  });

  it("includes fixed joints when includeFixed=true", () => {
    const specs = extractJointSpecs(TWO_LINK_ROBOT, { includeFixed: true });
    expect(specs.map(s => s.name).sort()).toEqual(["base", "elbow", "rotator", "shoulder", "slider"]);
  });

  it("carries joint limits through to the spec", () => {
    const specs = extractJointSpecs(TWO_LINK_ROBOT);
    const shoulder = specs.find(s => s.name === "shoulder")!;
    expect(shoulder.lower).toBeCloseTo(-3.14159, 5);
    expect(shoulder.upper).toBeCloseTo(3.14159, 5);
  });

  it("returns null limits for joints without a limit field (continuous)", () => {
    const specs = extractJointSpecs(TWO_LINK_ROBOT);
    const rotator = specs.find(s => s.name === "rotator")!;
    expect(rotator.lower).toBeNull();
    expect(rotator.upper).toBeNull();
    expect(rotator.jointType).toBe("continuous");
  });

  it("distinguishes prismatic from revolute joints", () => {
    const specs = extractJointSpecs(TWO_LINK_ROBOT);
    const slider = specs.find(s => s.name === "slider")!;
    expect(slider.jointType).toBe("prismatic");
    expect(slider.upper).toBe(0.5);
  });

  it("defaults jointType to revolute when undeclared", () => {
    const robot: UrdfRobotLike = { joints: { mystery: { limit: null } } };
    const specs = extractJointSpecs(robot);
    expect(specs[0]!.jointType).toBe("revolute");
  });
});
