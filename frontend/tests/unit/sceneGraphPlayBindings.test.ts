/**
 * SCENEGRAPH-CANVAS-ANIM-1 slice 2 — unit tests for the bind-channels
 * pure helper `buildPatchedSceneGraphBody`. Network-free.
 *
 * Backlog: SCENEGRAPH-CANVAS-ANIM-1 slice 2.
 */
import { describe, it, expect } from "vitest";
import {
  buildPatchedSceneGraphBody,
  SCENE_GRAPH_PLAY_SHAPE_IRI,
} from "../../composables/useSceneGraphPlay";

const URDF = "urdf-app-id-abc";
const JOINTS = [
  { name: "shoulder", type: "revolute" },
  { name: "elbow",    type: "revolute" },
  { name: "wrist",    type: "revolute" },
  { name: "base",     type: "fixed" },
];

describe("buildPatchedSceneGraphBody", () => {
  it("preserves the URDF appId and shape IRI", () => {
    const body = buildPatchedSceneGraphBody(URDF, null, {}, JOINTS);
    expect(body.urdfFileReferenceAppId).toBe(URDF);
    expect(body.mappingRecipeShape).toBe(SCENE_GRAPH_PLAY_SHAPE_IRI);
    expect(body.templateKind).toBe("MAPPING_RECIPE");
  });

  it("includes the TS reference appId when provided", () => {
    const body = buildPatchedSceneGraphBody(URDF, "ts-ref-1", {}, JOINTS);
    expect(body.jointTimeseriesReferenceAppId).toBe("ts-ref-1");
  });

  it("omits the TS reference appId when null", () => {
    const body = buildPatchedSceneGraphBody(URDF, null, {}, JOINTS);
    expect(body.jointTimeseriesReferenceAppId).toBeUndefined();
  });

  it("omits the TS reference appId when empty string", () => {
    const body = buildPatchedSceneGraphBody(URDF, "", {}, JOINTS);
    expect(body.jointTimeseriesReferenceAppId).toBeUndefined();
  });

  it("builds JSON-stringified channel bindings from the channel map", () => {
    const channelMap = { shoulder: "ch-1", elbow: "ch-2", wrist: null, base: null };
    const body = buildPatchedSceneGraphBody(URDF, "ts-ref-1", channelMap, JOINTS);
    const parsed = JSON.parse(body.jointChannelBindings as string);
    expect(parsed).toHaveLength(2);
    expect(parsed).toContainEqual({ joint: "shoulder", channelSelector: "ch-1" });
    expect(parsed).toContainEqual({ joint: "elbow",    channelSelector: "ch-2" });
  });

  it("omits bindings entirely when no channel is assigned", () => {
    const channelMap = { shoulder: null, elbow: null, wrist: null };
    const body = buildPatchedSceneGraphBody(URDF, "ts-1", channelMap, JOINTS);
    expect(body.jointChannelBindings).toBeUndefined();
  });

  it("omits joints with blank or whitespace-only selectors", () => {
    const channelMap = { shoulder: "ch-1", elbow: "   ", wrist: "" };
    const body = buildPatchedSceneGraphBody(URDF, "ts-1", channelMap, JOINTS);
    const parsed = JSON.parse(body.jointChannelBindings as string);
    expect(parsed).toHaveLength(1);
    expect(parsed[0].joint).toBe("shoulder");
  });

  it("trims whitespace from channel selectors", () => {
    const channelMap = { shoulder: "  ch-1  " };
    const body = buildPatchedSceneGraphBody(URDF, "ts-1", channelMap, [{ name: "shoulder" }]);
    const parsed = JSON.parse(body.jointChannelBindings as string);
    expect(parsed[0].channelSelector).toBe("ch-1");
  });

  it("includes all joints passed in joints array (incl. fixed — caller filters)", () => {
    const channelMap = { base: "ch-base" };
    const body = buildPatchedSceneGraphBody(URDF, "ts-1", channelMap, [{ name: "base" }]);
    const parsed = JSON.parse(body.jointChannelBindings as string);
    expect(parsed[0].joint).toBe("base");
  });

  it("handles an empty joints array without throwing", () => {
    const body = buildPatchedSceneGraphBody(URDF, "ts-1", {}, []);
    expect(body.urdfFileReferenceAppId).toBe(URDF);
    expect(body.jointChannelBindings).toBeUndefined();
  });
});
