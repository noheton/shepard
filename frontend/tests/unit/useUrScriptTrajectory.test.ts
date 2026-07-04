/**
 * URSCRIPT-TRAJECTORY-1-FE — unit tests for the URScript trajectory composable's
 * pure helpers.
 *
 * The UR-robot sibling of useKrlTrajectory. These pure helpers build the template
 * body, the default name, the .urscript/.script eligibility test, and locate an
 * existing template from annotations — exercised without mounting Vue (the network
 * createTemplate + materialize paths are covered by the backend integration tests
 * + the component test).
 */
import { describe, it, expect } from "vitest";
import {
  URSCRIPT_TRAJECTORY_SHAPE_IRI,
  URSCRIPT_TEMPLATE_PREDICATE,
  buildUrScriptTrajectoryTemplateBody,
  defaultTrajectoryNameFor,
  isUrScriptFile,
  findUrScriptTrajectoryTemplateAppId,
} from "../../composables/useUrScriptTrajectory";

describe("useUrScriptTrajectory — buildUrScriptTrajectoryTemplateBody", () => {
  it("emits a MAPPING_RECIPE body with the shape IRI + all required bindings", () => {
    const body = buildUrScriptTrajectoryTemplateBody({
      urscriptFileReferenceAppId: "us-1",
      urdfFileReferenceAppId: "urdf-1",
      targetDataObjectAppId: "do-1",
      timeseriesContainerAppId: "tsc-1",
    });
    expect(body.templateKind).toBe("MAPPING_RECIPE");
    expect(body.mappingRecipeShape).toBe(URSCRIPT_TRAJECTORY_SHAPE_IRI);
    expect(body.urscriptFileReferenceAppId).toBe("us-1");
    expect(body.urdfFileReferenceAppId).toBe("urdf-1");
    expect(body.targetDataObjectAppId).toBe("do-1");
    expect(body.timeseriesContainerAppId).toBe("tsc-1");
  });

  it("does not include a dat field (URScript has no .dat companion)", () => {
    const body = buildUrScriptTrajectoryTemplateBody({
      urscriptFileReferenceAppId: "us-1",
      urdfFileReferenceAppId: "urdf-1",
      targetDataObjectAppId: "do-1",
      timeseriesContainerAppId: "tsc-1",
    });
    expect(body).not.toHaveProperty("datFileReferenceAppIds");
  });

  it("shape IRI is distinct from KrlTrajectoryShape", () => {
    expect(URSCRIPT_TRAJECTORY_SHAPE_IRI).not.toBe(
      "http://semantics.dlr.de/shepard/transform#KrlTrajectoryShape",
    );
    expect(URSCRIPT_TRAJECTORY_SHAPE_IRI).toBe(
      "http://semantics.dlr.de/shepard/transform#UrScriptTrajectoryShape",
    );
  });
});

describe("useUrScriptTrajectory — isUrScriptFile", () => {
  it("accepts .urscript and .script (case-insensitive)", () => {
    expect(isUrScriptFile("pick_and_place.urscript")).toBe(true);
    expect(isUrScriptFile("pick_and_place.URSCRIPT")).toBe(true);
    expect(isUrScriptFile("move_j.script")).toBe(true);
    expect(isUrScriptFile("move_j.SCRIPT")).toBe(true);
  });

  it("rejects KRL extensions, other extensions, and nullish input", () => {
    expect(isUrScriptFile("weld.src")).toBe(false);
    expect(isUrScriptFile("weld.krl")).toBe(false);
    expect(isUrScriptFile("robot.urdf")).toBe(false);
    expect(isUrScriptFile("")).toBe(false);
    expect(isUrScriptFile(null)).toBe(false);
    expect(isUrScriptFile(undefined)).toBe(false);
  });
});

describe("useUrScriptTrajectory — defaultTrajectoryNameFor", () => {
  it("strips a trailing .urscript extension and appends a suffix", () => {
    expect(defaultTrajectoryNameFor("pick_and_place.urscript")).toBe(
      "pick_and_place — trajectory",
    );
    expect(defaultTrajectoryNameFor("move_j.URSCRIPT")).toBe("move_j — trajectory");
  });

  it("strips a trailing .script extension", () => {
    expect(defaultTrajectoryNameFor("welding_path.script")).toBe("welding_path — trajectory");
  });

  it("falls back to a generic label for empty input", () => {
    expect(defaultTrajectoryNameFor("")).toBe("URScript trajectory");
    expect(defaultTrajectoryNameFor(null)).toBe("URScript trajectory");
  });
});

describe("useUrScriptTrajectory — findUrScriptTrajectoryTemplateAppId", () => {
  it("returns the template appId from the mapping back-annotation", () => {
    const found = findUrScriptTrajectoryTemplateAppId([
      { propertyIRI: "urn:shepard:other", valueName: "x" },
      { propertyIRI: URSCRIPT_TEMPLATE_PREDICATE, valueName: "tmpl-99" },
    ]);
    expect(found).toBe("tmpl-99");
  });

  it("returns null when no mapping annotation is present", () => {
    expect(
      findUrScriptTrajectoryTemplateAppId([{ propertyIRI: "urn:shepard:urdf:x" }]),
    ).toBeNull();
  });

  it("returns null for empty / nullish input", () => {
    expect(findUrScriptTrajectoryTemplateAppId([])).toBeNull();
    expect(findUrScriptTrajectoryTemplateAppId(null)).toBeNull();
    expect(findUrScriptTrajectoryTemplateAppId(undefined)).toBeNull();
  });

  it("ignores a blank valueName", () => {
    expect(
      findUrScriptTrajectoryTemplateAppId([
        { propertyIRI: URSCRIPT_TEMPLATE_PREDICATE, valueName: "  " },
      ]),
    ).toBeNull();
  });

  it("does not match the KRL template predicate", () => {
    const found = findUrScriptTrajectoryTemplateAppId([
      { propertyIRI: "urn:shepard:mapping:krl-trajectory-template-appId", valueName: "tmpl-krl" },
    ]);
    expect(found).toBeNull();
  });
});
