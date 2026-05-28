/**
 * UrdfChannelPicker — unit tests for the channel ↔ joint preselection logic.
 *
 * Component is NOT mounted (matches the project's Vitest pattern, see
 * Trace3DChannelPicker.test.ts). All logic lives in
 * `utils/urdfChannelPicker.ts`.
 *
 * Task: URDF-WEBVIEW-1 (aidocs/16).
 */
import { describe, it, expect } from "vitest";
import {
  type UrdfPickerChannel,
  type UrdfPickerJoint,
  URDF_JOINT_PREDICATE,
  preselectChannelForJoint,
  initialBinding,
  isBindingReady,
  resolveBoundChannels,
} from "~/utils/urdfChannelPicker";

// ── fixtures ──────────────────────────────────────────────────────────────────

const JOINTS: UrdfPickerJoint[] = [
  { name: "shoulder", jointType: "revolute" },
  { name: "elbow",    jointType: "revolute" },
  { name: "wrist",    jointType: "revolute" },
  { name: "base",     jointType: "fixed"    },
];

const CHANNELS_ANNOTATED: UrdfPickerChannel[] = [
  { shepardId: "id-1", symbolicName: "robot_arm", field: "joint_a", annotatedJoint: "shoulder" },
  { shepardId: "id-2", symbolicName: "robot_arm", field: "joint_b", annotatedJoint: "elbow"    },
  { shepardId: "id-3", symbolicName: "robot_arm", field: "joint_c" },
];

const CHANNELS_HEURISTIC: UrdfPickerChannel[] = [
  { shepardId: "id-1", symbolicName: "shoulder", field: "rad"   },
  { shepardId: "id-2", symbolicName: "joint_x",  field: "elbow" },
  { shepardId: "id-3", symbolicName: "irrelevant", field: "v"   },
];

// ── URDF_JOINT_PREDICATE ──────────────────────────────────────────────────────

describe("URDF_JOINT_PREDICATE", () => {
  it("uses the urn:shepard:* namespace per the predicate-namespace rule", () => {
    expect(URDF_JOINT_PREDICATE).toBe("urn:shepard:urdf:joint");
  });
});

// ── preselectChannelForJoint ─────────────────────────────────────────────────

describe("preselectChannelForJoint", () => {
  it("returns null for an empty joint name", () => {
    expect(preselectChannelForJoint("", CHANNELS_ANNOTATED)).toBeNull();
  });

  it("returns null when no channel matches", () => {
    expect(preselectChannelForJoint("shoulder", [])).toBeNull();
    expect(preselectChannelForJoint("nonexistent", CHANNELS_ANNOTATED)).toBeNull();
  });

  it("preselects the annotated channel (pass 1)", () => {
    expect(preselectChannelForJoint("shoulder", CHANNELS_ANNOTATED)).toBe("id-1");
    expect(preselectChannelForJoint("elbow",    CHANNELS_ANNOTATED)).toBe("id-2");
  });

  it("falls back to heuristic match on symbolicName (pass 2)", () => {
    expect(preselectChannelForJoint("shoulder", CHANNELS_HEURISTIC)).toBe("id-1");
  });

  it("falls back to heuristic match on field (pass 2)", () => {
    expect(preselectChannelForJoint("elbow", CHANNELS_HEURISTIC)).toBe("id-2");
  });

  it("matches case-insensitively for the heuristic pass", () => {
    const channels: UrdfPickerChannel[] = [
      { shepardId: "x", symbolicName: "SHOULDER", field: "v" },
    ];
    expect(preselectChannelForJoint("shoulder", channels)).toBe("x");
  });

  it("prefers an annotation over a heuristic match", () => {
    const channels: UrdfPickerChannel[] = [
      { shepardId: "h", symbolicName: "shoulder",  field: "v" }, // heuristic match
      { shepardId: "a", symbolicName: "irrelevant", field: "v", annotatedJoint: "shoulder" }, // annotation
    ];
    expect(preselectChannelForJoint("shoulder", channels)).toBe("a");
  });
});

// ── initialBinding ───────────────────────────────────────────────────────────

describe("initialBinding", () => {
  it("includes one entry per movable joint and skips fixed joints", () => {
    const b = initialBinding(JOINTS, CHANNELS_ANNOTATED);
    expect(Object.keys(b).sort()).toEqual(["elbow", "shoulder", "wrist"]);
    expect("base" in b).toBe(false);
  });

  it("auto-binds annotated channels", () => {
    const b = initialBinding(JOINTS, CHANNELS_ANNOTATED);
    expect(b.shoulder).toBe("id-1");
    expect(b.elbow).toBe("id-2");
  });

  it("leaves slots null when no channel matches", () => {
    const b = initialBinding(JOINTS, CHANNELS_ANNOTATED);
    expect(b.wrist).toBeNull();
  });

  it("returns an empty object when joints list is empty", () => {
    expect(initialBinding([], CHANNELS_ANNOTATED)).toEqual({});
  });
});

// ── isBindingReady ───────────────────────────────────────────────────────────

describe("isBindingReady", () => {
  it("is false when every slot is null", () => {
    expect(isBindingReady({ shoulder: null, elbow: null })).toBe(false);
  });

  it("is true when at least one slot has a channel", () => {
    expect(isBindingReady({ shoulder: "id-1", elbow: null })).toBe(true);
  });

  it("treats empty strings as unbound", () => {
    expect(isBindingReady({ shoulder: "" })).toBe(false);
  });
});

// ── resolveBoundChannels ─────────────────────────────────────────────────────

describe("resolveBoundChannels", () => {
  it("returns the channels resolved by the binding map", () => {
    const binding = { shoulder: "id-1", elbow: "id-2", wrist: null };
    const resolved = resolveBoundChannels(binding, CHANNELS_ANNOTATED);
    expect(resolved.shoulder?.shepardId).toBe("id-1");
    expect(resolved.elbow?.shepardId).toBe("id-2");
    expect("wrist" in resolved).toBe(false);
  });

  it("silently drops bindings whose channel is missing from the list", () => {
    const binding = { shoulder: "id-1", elbow: "id-missing" };
    const resolved = resolveBoundChannels(binding, CHANNELS_ANNOTATED);
    expect(resolved.shoulder?.shepardId).toBe("id-1");
    expect("elbow" in resolved).toBe(false);
  });
});
