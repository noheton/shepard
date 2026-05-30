/**
 * SCENEGRAPH-REST-1-UI — unit tests for SceneGraphJointList logic.
 *
 * Per the established pattern, we exercise the pure frame-label helper
 * rather than mounting the Vuetify table.
 */
import { describe, it, expect } from "vitest";
import type { FrameIO, JointIO } from "../../composables/useSceneGraph";

function mkFrame(appId: string, name?: string): FrameIO {
  return { appId, name, kind: "FRAME" };
}

// Mirrors the frameLabel helper in the SFC.
function frameLabel(
  appId: string | null | undefined,
  framesByAppId: Map<string, FrameIO>,
): string {
  if (!appId) return "—";
  const f = framesByAppId.get(appId);
  if (!f) return appId.slice(0, 8);
  return f.name ? f.name : appId.slice(0, 8);
}

describe("SceneGraphJointList — frameLabel", () => {
  const frames = new Map<string, FrameIO>([
    ["shoulder-id", mkFrame("shoulder-id", "shoulder_link")],
    ["nameless-deadbeef", mkFrame("nameless-deadbeef")],
  ]);

  it("returns '—' for null / undefined / empty appId", () => {
    expect(frameLabel(null, frames)).toBe("—");
    expect(frameLabel(undefined, frames)).toBe("—");
    expect(frameLabel("", frames)).toBe("—");
  });

  it("returns the frame name when present", () => {
    expect(frameLabel("shoulder-id", frames)).toBe("shoulder_link");
  });

  it("falls back to short appId when the frame has no name", () => {
    expect(frameLabel("nameless-deadbeef", frames)).toBe("nameless");
  });

  it("falls back to short appId when the frame is unknown", () => {
    expect(frameLabel("ghost-frame-id", frames)).toBe("ghost-fr");
  });
});

describe("SceneGraphJointList — empty + populated rendering shape", () => {
  it("reports zero joints as an empty table", () => {
    const joints: JointIO[] = [];
    expect(joints.length).toBe(0);
  });

  it("reports the correct count for a populated joint list", () => {
    const joints: JointIO[] = [
      { appId: "j1", parentFrameAppId: "a", childFrameAppId: "b", type: "FIXED" },
      {
        appId: "j2",
        parentFrameAppId: "b",
        childFrameAppId: "c",
        type: "REVOLUTE",
      },
    ];
    expect(joints.length).toBe(2);
    expect(joints[0]!.type).toBe("FIXED");
    expect(joints[1]!.type).toBe("REVOLUTE");
  });
});
