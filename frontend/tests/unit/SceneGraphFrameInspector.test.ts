/**
 * SCENEGRAPH-REST-1-UI — unit tests for the SceneGraphFrameInspector logic.
 *
 * Per the unit-test pattern in this repo (see AnnotationChip /
 * CreateDataReferenceDialog tests), the Vuetify component tree is not mounted
 * — we exercise the dirty-detection logic, the parent-items menu shape, and
 * the JSON-LD payload builder.
 */
import { describe, it, expect } from "vitest";
import type { FrameIO } from "../../composables/useSceneGraph";

function mkFrame(appId: string, overrides: Partial<FrameIO> = {}): FrameIO {
  return {
    appId,
    name: appId,
    parentFrameAppId: null,
    x: 0,
    y: 0,
    z: 0,
    rx: 0,
    ry: 0,
    rz: 0,
    kind: "FRAME",
    ...overrides,
  };
}

// Mirrors the isDirty computation in the SFC.
function isDirty(
  original: FrameIO,
  edited: {
    name: string;
    parentFrameAppId: string | null;
    kind: string;
    x: number;
    y: number;
    z: number;
    rx: number;
    ry: number;
    rz: number;
  },
): boolean {
  return (
    edited.name !== (original.name ?? "") ||
    (edited.parentFrameAppId ?? null) !== (original.parentFrameAppId ?? null) ||
    edited.kind !== (original.kind ?? "FRAME") ||
    edited.x !== (original.x ?? 0) ||
    edited.y !== (original.y ?? 0) ||
    edited.z !== (original.z ?? 0) ||
    edited.rx !== (original.rx ?? 0) ||
    edited.ry !== (original.ry ?? 0) ||
    edited.rz !== (original.rz ?? 0)
  );
}

// Mirrors the parentItems computation in the SFC.
function parentItems(
  selectedAppId: string,
  candidates: FrameIO[],
): Array<{ title: string; value: string }> {
  const arr = candidates
    .filter((f) => f.appId !== selectedAppId)
    .map((f) => ({
      title: f.name ? `${f.name} (${f.appId.slice(0, 8)})` : f.appId.slice(0, 8),
      value: f.appId,
    }));
  arr.unshift({ title: "(no parent — make root)", value: "" });
  return arr;
}

// Mirrors the copyAsJsonLd payload builder.
function buildJsonLdDoc(
  frame: FrameIO,
  form: {
    name: string;
    parentFrameAppId: string | null;
    kind: string;
    x: number;
    y: number;
    z: number;
    rx: number;
    ry: number;
    rz: number;
  },
): Record<string, unknown> {
  return {
    "@context": "https://schema.shepard.dlr.de/v2/scene-graph",
    "@type": "CoordinateFrame",
    appId: frame.appId,
    ...form,
  };
}

describe("SceneGraphFrameInspector — isDirty", () => {
  const original = mkFrame("a", {
    name: "shoulder",
    x: 0.5,
    rz: 1.57,
    kind: "JOINT",
  });

  function form(overrides: Partial<Record<string, unknown>> = {}): Parameters<
    typeof isDirty
  >[1] {
    return {
      name: "shoulder",
      parentFrameAppId: null,
      kind: "JOINT",
      x: 0.5,
      y: 0,
      z: 0,
      rx: 0,
      ry: 0,
      rz: 1.57,
      ...overrides,
    } as Parameters<typeof isDirty>[1];
  }

  it("returns false when nothing changed", () => {
    expect(isDirty(original, form())).toBe(false);
  });

  it("returns true when the name changed", () => {
    expect(isDirty(original, form({ name: "wrist" }))).toBe(true);
  });

  it("returns true when a translation field changed", () => {
    expect(isDirty(original, form({ x: 0.501 }))).toBe(true);
  });

  it("returns true when the kind changed", () => {
    expect(isDirty(original, form({ kind: "TOOL" }))).toBe(true);
  });

  it("treats null parent and undefined parent as equal", () => {
    const frame = mkFrame("a", {
      name: "shoulder",
      x: 0.5,
      rz: 1.57,
      kind: "JOINT",
      parentFrameAppId: undefined as unknown as null,
    });
    expect(isDirty(frame, form({ parentFrameAppId: null }))).toBe(false);
  });
});

describe("SceneGraphFrameInspector — parentItems", () => {
  const frames = [mkFrame("a"), mkFrame("b"), mkFrame("c")];

  it("excludes the selected frame from the parent menu", () => {
    const items = parentItems("b", frames);
    expect(items.map((i) => i.value)).not.toContain("b");
  });

  it("prepends a make-root option with empty string value", () => {
    const items = parentItems("a", frames);
    expect(items[0]).toEqual({
      title: "(no parent — make root)",
      value: "",
    });
  });

  it("includes other frames as candidates", () => {
    const items = parentItems("a", frames);
    expect(items.map((i) => i.value).sort()).toEqual(["", "b", "c"]);
  });

  it("falls back to short appId when the frame has no name", () => {
    const items = parentItems("x", [
      mkFrame("frame-deadbeef", { name: undefined }),
    ]);
    const candidate = items.find((i) => i.value === "frame-deadbeef");
    expect(candidate?.title).toBe("frame-de");
  });
});

describe("SceneGraphFrameInspector — JSON-LD payload", () => {
  it("includes the schema context and CoordinateFrame type", () => {
    const f = mkFrame("a");
    const doc = buildJsonLdDoc(f, {
      name: "a",
      parentFrameAppId: null,
      kind: "FRAME",
      x: 0,
      y: 0,
      z: 0,
      rx: 0,
      ry: 0,
      rz: 0,
    });
    expect(doc["@context"]).toBe(
      "https://schema.shepard.dlr.de/v2/scene-graph",
    );
    expect(doc["@type"]).toBe("CoordinateFrame");
    expect(doc.appId).toBe("a");
  });

  it("carries the form values, not the original frame values", () => {
    const f = mkFrame("a", { x: 0 });
    const doc = buildJsonLdDoc(f, {
      name: "a",
      parentFrameAppId: null,
      kind: "FRAME",
      x: 1.234,
      y: 0,
      z: 0,
      rx: 0,
      ry: 0,
      rz: 0,
    });
    expect(doc.x).toBe(1.234);
  });
});
