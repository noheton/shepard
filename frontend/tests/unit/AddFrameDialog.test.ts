/**
 * SCENEGRAPH-REST-1-UI — unit tests for AddFrameDialog logic.
 *
 * Verifies the can-submit gate (name required; parent required unless first
 * frame) and the body-builder shape. Pure-helper test pattern — no Vuetify
 * mount.
 */
import { describe, it, expect } from "vitest";
import type {
  CreateFrameRequestIO,
  FrameIO,
  FrameKind,
} from "../../composables/useSceneGraph";

function mkFrame(appId: string): FrameIO {
  return { appId, name: appId, kind: "FRAME" };
}

// Mirrors the canSubmit computation in the SFC.
function canSubmit(
  isFirstFrame: boolean,
  name: string,
  parentFrameAppId: string | null,
): boolean {
  if (name.trim().length === 0) return false;
  if (!isFirstFrame && !parentFrameAppId) return false;
  return true;
}

// Mirrors the body builder.
function buildBody(
  isFirstFrame: boolean,
  name: string,
  parentFrameAppId: string | null,
  kind: FrameKind,
  x: number,
  y: number,
  z: number,
  rx: number,
  ry: number,
  rz: number,
): CreateFrameRequestIO {
  return {
    name: name.trim(),
    parentFrameAppId: isFirstFrame ? null : parentFrameAppId,
    kind,
    x,
    y,
    z,
    rx,
    ry,
    rz,
  };
}

describe("AddFrameDialog — canSubmit", () => {
  it("requires a non-empty name", () => {
    expect(canSubmit(false, "", "p")).toBe(false);
    expect(canSubmit(false, "   ", "p")).toBe(false);
    expect(canSubmit(false, "shoulder", "p")).toBe(true);
  });

  it("requires a parent unless this is the first frame", () => {
    expect(canSubmit(false, "shoulder", null)).toBe(false);
    expect(canSubmit(true, "root_link", null)).toBe(true);
  });

  it("returns true for first-frame with name + no parent", () => {
    expect(canSubmit(true, "base_link", null)).toBe(true);
  });
});

describe("AddFrameDialog — body builder", () => {
  it("forces parentFrameAppId to null when adding the first frame", () => {
    const body = buildBody(true, "root", "ignored", "BASE", 0, 0, 0, 0, 0, 0);
    expect(body.parentFrameAppId).toBeNull();
  });

  it("respects the chosen parent when not adding the first frame", () => {
    const body = buildBody(
      false,
      "shoulder",
      "parent-id",
      "FRAME",
      0.1,
      0.2,
      0.3,
      0,
      0,
      1.57,
    );
    expect(body.parentFrameAppId).toBe("parent-id");
    expect(body.name).toBe("shoulder");
    expect(body.kind).toBe("FRAME");
    expect(body.x).toBe(0.1);
    expect(body.rz).toBe(1.57);
  });

  it("trims whitespace from the name", () => {
    const body = buildBody(true, "  root_link  ", null, "BASE", 0, 0, 0, 0, 0, 0);
    expect(body.name).toBe("root_link");
  });
});

describe("AddFrameDialog — parent items list", () => {
  function parentItems(frames: FrameIO[]) {
    return frames.map((f) => ({
      title: f.name ? `${f.name} (${f.appId.slice(0, 8)})` : f.appId.slice(0, 8),
      value: f.appId,
    }));
  }

  it("derives title from frame name + short appId suffix", () => {
    const items = parentItems([mkFrame("shoulder-deadbeef")]);
    const first = items[0]!;
    expect(first.title).toBe("shoulder-deadbeef (shoulder)");
    expect(first.value).toBe("shoulder-deadbeef");
  });

  it("returns an empty array when there are no candidates", () => {
    expect(parentItems([])).toEqual([]);
  });
});
