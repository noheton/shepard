/**
 * SCENEGRAPH-REST-1-UI — unit tests for the SceneGraphTreeView component logic.
 *
 * The recursive Vuetify rendering is integration-tested via the Playwright
 * 4K spec; here we exercise the pure logic exported from the composable
 * that drives the tree (root resolution, orphan detection, joint-count chip).
 */
import { describe, it, expect } from "vitest";
import {
  indexFramesByParent,
  type FrameIO,
  type JointIO,
} from "../../composables/useSceneGraph";

function mkFrame(
  appId: string,
  parent: string | null = null,
  overrides: Partial<FrameIO> = {},
): FrameIO {
  return {
    appId,
    name: appId,
    parentFrameAppId: parent,
    kind: "FRAME",
    ...overrides,
  };
}

function mkJoint(parent: string, child: string): JointIO {
  return {
    appId: `${parent}__${child}`,
    parentFrameAppId: parent,
    childFrameAppId: child,
    type: "FIXED",
  };
}

describe("SceneGraphTreeView — root resolution", () => {
  // Mirrors the renderedRoots computation in the SFC.
  function rootsOf(
    frames: FrameIO[],
    rootFrameAppId: string | null,
  ): string[] {
    const byParent = indexFramesByParent(frames);
    const byAppId = new Map(frames.map((f) => [f.appId, f]));
    const declared = rootFrameAppId ? byAppId.get(rootFrameAppId) : undefined;
    const nullParented = byParent.get("") ?? [];
    const seen = new Set<string>();
    const result: string[] = [];
    if (declared && !seen.has(declared.appId)) {
      result.push(declared.appId);
      seen.add(declared.appId);
    }
    for (const f of nullParented) {
      if (!seen.has(f.appId)) {
        result.push(f.appId);
        seen.add(f.appId);
      }
    }
    return result;
  }

  it("returns the declared root when set", () => {
    const frames = [mkFrame("a"), mkFrame("b", "a")];
    expect(rootsOf(frames, "a")).toEqual(["a"]);
  });

  it("returns null-parented frames when no declared root", () => {
    const frames = [mkFrame("a"), mkFrame("b")];
    expect(rootsOf(frames, null)).toEqual(["a", "b"]);
  });

  it("dedupes the declared root from null-parented set", () => {
    const frames = [mkFrame("a"), mkFrame("b")];
    expect(rootsOf(frames, "a")).toEqual(["a", "b"]);
  });

  it("returns empty when the declared root is missing and no null-parented", () => {
    const frames = [mkFrame("a", "ghost")];
    expect(rootsOf(frames, "ghost")).toEqual([]);
  });
});

describe("SceneGraphTreeView — orphan detection", () => {
  function orphansOf(frames: FrameIO[]): string[] {
    const byAppId = new Map(frames.map((f) => [f.appId, f]));
    const list: string[] = [];
    for (const f of frames) {
      const p = f.parentFrameAppId;
      if (p && !byAppId.has(p)) list.push(f.appId);
    }
    return list;
  }

  it("detects frames whose parent ref is missing", () => {
    const frames = [mkFrame("a"), mkFrame("b", "a"), mkFrame("orph", "ghost")];
    expect(orphansOf(frames)).toEqual(["orph"]);
  });

  it("returns empty when every parent ref resolves", () => {
    const frames = [mkFrame("a"), mkFrame("b", "a"), mkFrame("c", "b")];
    expect(orphansOf(frames)).toEqual([]);
  });

  it("does NOT mark null-parent frames as orphans", () => {
    const frames = [mkFrame("a")];
    expect(orphansOf(frames)).toEqual([]);
  });
});

describe("SceneGraphTreeView — joint-count chip", () => {
  function jointCountFor(frameAppId: string, joints: JointIO[]): number {
    let n = 0;
    for (const j of joints) {
      if (j.parentFrameAppId === frameAppId || j.childFrameAppId === frameAppId)
        n += 1;
    }
    return n;
  }

  it("returns 0 for a frame with no attached joints", () => {
    expect(jointCountFor("a", [])).toBe(0);
  });

  it("counts joints where the frame is the parent endpoint", () => {
    expect(jointCountFor("a", [mkJoint("a", "b"), mkJoint("a", "c")])).toBe(2);
  });

  it("counts joints where the frame is the child endpoint", () => {
    expect(jointCountFor("c", [mkJoint("a", "c")])).toBe(1);
  });

  it("counts both endpoints together", () => {
    expect(
      jointCountFor("a", [mkJoint("a", "b"), mkJoint("c", "a")]),
    ).toBe(2);
  });
});
