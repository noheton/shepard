/**
 * SCENEGRAPH-NAV-02 — unit tests for the OpenInSceneGraphButton helpers.
 *
 * Mirrors the inline-helper test pattern used by EditFileReferenceDialog
 * and RunKrlPreviewButton. Tests the pure predicate-detection /
 * scene-appId-extraction / route-builder logic without mounting Vue.
 */
import { describe, it, expect } from "vitest";
import {
  hasSceneGraphRole,
  findSceneAppId,
  sceneGraphRouteFor,
  RDK_PREFIX,
  URDF_PREFIX,
  SCENE_APP_ID_PREDICATE,
  RDK_ROLE_PREDICATE,
  URDF_ROLE_PREDICATE,
  type AnnotationLike,
} from "../../components/container/file/openInSceneGraphButtonHelpers";

const ann = (propertyIRI: string, valueIRI: string = ""): AnnotationLike => ({
  propertyIRI,
  valueIRI,
});

describe("openInSceneGraphButtonHelpers — hasSceneGraphRole", () => {
  it("returns false for no annotations and no filename signal", () => {
    expect(hasSceneGraphRole([], "calibration.json")).toBe(false);
    expect(hasSceneGraphRole(null, null)).toBe(false);
    expect(hasSceneGraphRole(undefined, undefined)).toBe(false);
  });

  it("returns true when any urn:shepard:rdk:* annotation is present", () => {
    expect(
      hasSceneGraphRole([ann(`${RDK_PREFIX}appVersion`, "5.5.3")], "MFZ.rdk"),
    ).toBe(true);
    expect(
      hasSceneGraphRole([ann(`${RDK_PREFIX}cadRef`, "x.dae")], "random.bin"),
    ).toBe(true);
  });

  it("accepts the forward-compat explicit role predicates", () => {
    expect(
      hasSceneGraphRole(
        [ann(RDK_ROLE_PREDICATE, "scene-graph-source")],
        "random.bin",
      ),
    ).toBe(true);
    expect(
      hasSceneGraphRole([ann(URDF_ROLE_PREDICATE, "urdf")], "random.bin"),
    ).toBe(true);
  });

  it("returns true when any urn:shepard:urdf:* annotation is present", () => {
    expect(
      hasSceneGraphRole(
        [ann(`${URDF_PREFIX}joint`, "joint_a1")],
        "anything.bin",
      ),
    ).toBe(true);
  });

  it("returns true when the back-annotation is present", () => {
    expect(
      hasSceneGraphRole(
        [ann(SCENE_APP_ID_PREDICATE, "019e79be-b880-7438-82df-4163625862b7")],
        "anything.bin",
      ),
    ).toBe(true);
  });

  it("returns true on a .urdf / .rdk filename even without annotations", () => {
    expect(hasSceneGraphRole([], "kr210_r2700_2.urdf")).toBe(true);
    expect(hasSceneGraphRole([], "MFZ.rdk")).toBe(true);
    expect(hasSceneGraphRole([], "MFZ.RDK")).toBe(true); // case-insensitive
  });

  it("ignores unrelated annotation predicates", () => {
    expect(
      hasSceneGraphRole(
        [ann("urn:shepard:spatial:axis", "x"), ann("dcterms:title", "foo")],
        "report.pdf",
      ),
    ).toBe(false);
  });

  it("treats blank / null propertyIRIs as no-signal", () => {
    expect(
      hasSceneGraphRole(
        [ann("", "value"), { propertyIRI: null }, { propertyIRI: undefined }],
        "report.pdf",
      ),
    ).toBe(false);
  });
});

describe("openInSceneGraphButtonHelpers — findSceneAppId", () => {
  it("returns null when the back-annotation is absent", () => {
    expect(findSceneAppId([])).toBeNull();
    expect(findSceneAppId(null)).toBeNull();
    expect(findSceneAppId([ann(`${RDK_PREFIX}appVersion`, "5.5.3")])).toBeNull();
  });

  it("returns the back-annotation value when present", () => {
    const scene = "019e79be-b880-7438-82df-4163625862b7";
    expect(
      findSceneAppId([ann(SCENE_APP_ID_PREDICATE, scene)]),
    ).toBe(scene);
  });

  it("returns null when the back-annotation value is blank or whitespace", () => {
    expect(findSceneAppId([ann(SCENE_APP_ID_PREDICATE, "")])).toBeNull();
    expect(findSceneAppId([ann(SCENE_APP_ID_PREDICATE, "   ")])).toBeNull();
  });

  it("picks the first non-blank back-annotation value", () => {
    expect(
      findSceneAppId([
        ann(SCENE_APP_ID_PREDICATE, "first-appid"),
        ann(SCENE_APP_ID_PREDICATE, "second-appid"),
      ]),
    ).toBe("first-appid");
  });
});

describe("openInSceneGraphButtonHelpers — sceneGraphRouteFor", () => {
  it("builds the canonical /scene-graphs/{appId} route", () => {
    expect(sceneGraphRouteFor("019e79be-b880-7438-82df-4163625862b7")).toBe(
      "/scene-graphs/019e79be-b880-7438-82df-4163625862b7",
    );
  });

  it("URL-encodes the appId so a stray slash never breaks the router", () => {
    expect(sceneGraphRouteFor("bad/id")).toBe("/scene-graphs/bad%2Fid");
  });
});
