/**
 * SCENEGRAPH-REST-1-UI — unit tests for the useSceneGraph composable's
 * pure helpers.
 *
 * Status-to-message mapping, URDF filename sanitisation, parent-index
 * building, and descendant counting. The wire I/O is integration-tested
 * via the Playwright 4K spec; this file exercises the parts that don't
 * need a Vuetify / fetch harness.
 */
import { describe, it, expect } from "vitest";
import {
  countDescendants,
  indexFramesByParent,
  sceneGraphErrorMessageForStatus,
  urdfDownloadFilename,
  type FrameIO,
} from "../../composables/useSceneGraph";

// ── Helpers ──────────────────────────────────────────────────────────────────

function mkFrame(
  appId: string,
  parent: string | null = null,
  overrides: Partial<FrameIO> = {},
): FrameIO {
  return {
    appId,
    name: appId,
    parentFrameAppId: parent,
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

// ── Tests ────────────────────────────────────────────────────────────────────

describe("useSceneGraph — sceneGraphErrorMessageForStatus", () => {
  it("returns the auth-expired message for 401", () => {
    expect(sceneGraphErrorMessageForStatus(401)).toMatch(/sign in expired/i);
  });

  it("returns the write-access message for 403", () => {
    expect(sceneGraphErrorMessageForStatus(403)).toMatch(/write access/i);
  });

  it("returns the not-found message for 404", () => {
    expect(sceneGraphErrorMessageForStatus(404)).toMatch(/not found/i);
  });

  it("returns the conflict message for 409", () => {
    expect(sceneGraphErrorMessageForStatus(409)).toMatch(/conflict/i);
  });

  it("falls back to detail on 400 + uses generic on unknown status", () => {
    expect(sceneGraphErrorMessageForStatus(400, "missing field")).toMatch(
      /missing field/i,
    );
    expect(sceneGraphErrorMessageForStatus(418)).toMatch(/HTTP 418/);
  });
});

describe("useSceneGraph — urdfDownloadFilename", () => {
  it("sanitises whitespace and slashes into underscores", () => {
    expect(urdfDownloadFilename("KR210 / cell", "appid-x")).toBe(
      "KR210_cell.urdf",
    );
  });

  it("falls back to scene_<short-appId> when name is empty", () => {
    expect(urdfDownloadFilename("", "019e7243-f995-7914")).toBe(
      "scene_019e7243.urdf",
    );
  });

  it("falls back to scene_<short-appId> when name is whitespace only", () => {
    expect(urdfDownloadFilename("   ", "abcd1234efgh")).toBe(
      "scene_abcd1234.urdf",
    );
  });

  it("collapses multiple underscores and strips leading/trailing", () => {
    expect(urdfDownloadFilename("__ a / b __", "x")).toBe("a_b.urdf");
  });

  it("removes filesystem-hostile characters from the name", () => {
    expect(urdfDownloadFilename('scene<>:"|?*name', "x")).toBe(
      "scene_name.urdf",
    );
  });
});

describe("useSceneGraph — indexFramesByParent", () => {
  it("returns empty map for null / empty input", () => {
    expect(indexFramesByParent(null).size).toBe(0);
    expect(indexFramesByParent(undefined).size).toBe(0);
    expect(indexFramesByParent([]).size).toBe(0);
  });

  it("groups null-parented frames under the empty-string key", () => {
    const map = indexFramesByParent([mkFrame("a"), mkFrame("b")]);
    expect(map.get("")?.map((f) => f.appId)).toEqual(["a", "b"]);
  });

  it("groups child frames under their parent's appId", () => {
    const map = indexFramesByParent([
      mkFrame("root"),
      mkFrame("c1", "root"),
      mkFrame("c2", "root"),
      mkFrame("gc1", "c1"),
    ]);
    expect(map.get("root")?.map((f) => f.appId)).toEqual(["c1", "c2"]);
    expect(map.get("c1")?.map((f) => f.appId)).toEqual(["gc1"]);
  });

  it("does not lose orphans (parent pointing at missing frame is still indexed)", () => {
    const map = indexFramesByParent([mkFrame("o", "ghost-parent")]);
    expect(map.get("ghost-parent")?.map((f) => f.appId)).toEqual(["o"]);
  });
});

describe("useSceneGraph — countDescendants", () => {
  it("returns 0 for a frame with no children", () => {
    const idx = indexFramesByParent([mkFrame("a")]);
    expect(countDescendants("a", idx)).toBe(0);
  });

  it("counts only descendants (not the frame itself)", () => {
    const idx = indexFramesByParent([
      mkFrame("root"),
      mkFrame("c1", "root"),
      mkFrame("c2", "root"),
      mkFrame("gc1", "c1"),
      mkFrame("gc2", "c1"),
    ]);
    expect(countDescendants("root", idx)).toBe(4);
    expect(countDescendants("c1", idx)).toBe(2);
    expect(countDescendants("c2", idx)).toBe(0);
  });

  it("handles unknown frame ids by returning 0", () => {
    const idx = indexFramesByParent([mkFrame("a")]);
    expect(countDescendants("ghost", idx)).toBe(0);
  });
});
