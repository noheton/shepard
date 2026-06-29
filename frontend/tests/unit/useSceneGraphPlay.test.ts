/**
 * V2CONV-B4-FE — unit tests for the scene-graph play composable's pure helpers.
 *
 * The bespoke scene-graph subsystem dissolved into a MAPPING_RECIPE view-shape
 * (aidocs/platform/191 decision #2). These pure helpers build the template body,
 * the play route, and locate an existing template from annotations — exercised
 * without mounting Vue (the network `createTemplate` path is covered by the
 * backend integration tests + the component test).
 */
import { describe, it, expect } from "vitest";
import {
  SCENE_GRAPH_PLAY_SHAPE_IRI,
  MAPPING_TEMPLATE_PREDICATE,
  buildSceneGraphPlayTemplateBody,
  default3dViewNameFor,
  sceneGraphPlayRouteFor,
  findSceneGraphTemplateAppId,
  fetchTemplateKind,
} from "../../composables/useSceneGraphPlay";

describe("useSceneGraphPlay — buildSceneGraphPlayTemplateBody", () => {
  it("emits a minimal MAPPING_RECIPE body with the shape IRI + URDF appId", () => {
    const body = buildSceneGraphPlayTemplateBody({
      urdfFileReferenceAppId: "urdf-1",
    });
    expect(body.templateKind).toBe("MAPPING_RECIPE");
    expect(body.mappingRecipeShape).toBe(SCENE_GRAPH_PLAY_SHAPE_IRI);
    expect(body.urdfFileReferenceAppId).toBe("urdf-1");
    expect(body.jointTimeseriesReferenceAppId).toBeUndefined();
    expect(body.jointChannelBindings).toBeUndefined();
  });

  it("includes the joint TS reference + stringified bindings when supplied", () => {
    const body = buildSceneGraphPlayTemplateBody({
      urdfFileReferenceAppId: "urdf-1",
      jointTimeseriesReferenceAppId: "ts-1",
      jointChannelBindings: [{ joint: "joint_1", channelSelector: "sel-1" }],
    });
    expect(body.jointTimeseriesReferenceAppId).toBe("ts-1");
    expect(body.jointChannelBindings).toBe(
      JSON.stringify([{ joint: "joint_1", channelSelector: "sel-1" }]),
    );
  });

  it("omits empty bindings array", () => {
    const body = buildSceneGraphPlayTemplateBody({
      urdfFileReferenceAppId: "urdf-1",
      jointChannelBindings: [],
    });
    expect(body.jointChannelBindings).toBeUndefined();
  });
});

describe("useSceneGraphPlay — default3dViewNameFor", () => {
  it("strips a trailing .urdf extension", () => {
    expect(default3dViewNameFor("kr210.urdf")).toBe("kr210");
  });

  it("strips a trailing .rdk extension (case-insensitive)", () => {
    expect(default3dViewNameFor("station.RDK")).toBe("station");
  });

  it("falls back to a generic label for empty input", () => {
    expect(default3dViewNameFor("")).toBe("3D view");
    expect(default3dViewNameFor(null)).toBe("3D view");
  });

  it("returns the name unchanged when no known extension", () => {
    expect(default3dViewNameFor("robot-cell")).toBe("robot-cell");
  });
});

describe("useSceneGraphPlay — sceneGraphPlayRouteFor", () => {
  it("builds the play route encoding the appId", () => {
    expect(sceneGraphPlayRouteFor("abc-123")).toBe("/scene-graphs/play/abc-123");
  });

  it("url-encodes special characters", () => {
    expect(sceneGraphPlayRouteFor("a b")).toBe("/scene-graphs/play/a%20b");
  });
});

describe("useSceneGraphPlay — findSceneGraphTemplateAppId", () => {
  it("returns the template appId from the mapping back-annotation", () => {
    const found = findSceneGraphTemplateAppId([
      { propertyIRI: "urn:shepard:other", valueName: "x" },
      { propertyIRI: MAPPING_TEMPLATE_PREDICATE, valueName: "tmpl-99" },
    ]);
    expect(found).toBe("tmpl-99");
  });

  it("returns null when no mapping annotation is present", () => {
    expect(findSceneGraphTemplateAppId([{ propertyIRI: "urn:shepard:rdk:x" }])).toBeNull();
  });

  it("returns null for empty / nullish input", () => {
    expect(findSceneGraphTemplateAppId([])).toBeNull();
    expect(findSceneGraphTemplateAppId(null)).toBeNull();
    expect(findSceneGraphTemplateAppId(undefined)).toBeNull();
  });

  it("ignores a blank valueName", () => {
    expect(
      findSceneGraphTemplateAppId([{ propertyIRI: MAPPING_TEMPLATE_PREDICATE, valueName: "  " }]),
    ).toBeNull();
  });
});

describe("useSceneGraphPlay — fetchTemplateKind", () => {
  // The network path (useAuth + fetch against /v2/templates/{appId}) is covered by
  // the backend integration tests + the play-page component test. Here we only
  // exercise the pure guard: an empty/blank appId resolves to null WITHOUT touching
  // useAuth or fetch (the `if (!templateAppId) return null` early return), so the
  // helper is safe to call before a route param has populated.
  it("returns null for an empty appId without performing network I/O", async () => {
    await expect(fetchTemplateKind("")).resolves.toBeNull();
  });
});
