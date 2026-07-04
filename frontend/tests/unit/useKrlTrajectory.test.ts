/**
 * V2CONV-B5-FE — unit tests for the KRL trajectory composable's pure helpers.
 *
 * The bespoke /v2/krl/interpret subsystem dissolved into a MAPPING_RECIPE
 * transform-shape (aidocs/platform/191 decision #2). These pure helpers build
 * the template body, the default name, the .src/.krl eligibility test, and
 * locate an existing template from annotations — exercised without mounting Vue
 * (the network createTemplate + materialize paths are covered by the backend
 * integration tests + the component test).
 */
import { describe, it, expect } from "vitest";
import {
  KRL_TRAJECTORY_SHAPE_IRI,
  KRL_TEMPLATE_PREDICATE,
  buildKrlTrajectoryTemplateBody,
  defaultTrajectoryNameFor,
  isKrlSrcFile,
  findKrlTrajectoryTemplateAppId,
} from "../../composables/useKrlTrajectory";

describe("useKrlTrajectory — buildKrlTrajectoryTemplateBody", () => {
  it("emits a MAPPING_RECIPE body with the shape IRI + all required bindings", () => {
    const body = buildKrlTrajectoryTemplateBody({
      srcFileReferenceAppId: "src-1",
      urdfFileReferenceAppId: "urdf-1",
      targetDataObjectAppId: "do-1",
      timeseriesContainerAppId: "tsc-1",
    });
    expect(body.templateKind).toBe("MAPPING_RECIPE");
    expect(body.mappingRecipeShape).toBe(KRL_TRAJECTORY_SHAPE_IRI);
    expect(body.srcFileReferenceAppId).toBe("src-1");
    expect(body.urdfFileReferenceAppId).toBe("urdf-1");
    expect(body.targetDataObjectAppId).toBe("do-1");
    expect(body.timeseriesContainerAppId).toBe("tsc-1");
    expect(body.datFileReferenceAppIds).toBeUndefined();
  });

  it("includes stringified .dat appIds when supplied", () => {
    const body = buildKrlTrajectoryTemplateBody({
      srcFileReferenceAppId: "src-1",
      urdfFileReferenceAppId: "urdf-1",
      targetDataObjectAppId: "do-1",
      timeseriesContainerAppId: "tsc-1",
      datFileReferenceAppIds: ["d-1", "d-2"],
    });
    expect(body.datFileReferenceAppIds).toBe(JSON.stringify(["d-1", "d-2"]));
  });

  it("omits an empty .dat array", () => {
    const body = buildKrlTrajectoryTemplateBody({
      srcFileReferenceAppId: "src-1",
      urdfFileReferenceAppId: "urdf-1",
      targetDataObjectAppId: "do-1",
      timeseriesContainerAppId: "tsc-1",
      datFileReferenceAppIds: [],
    });
    expect(body.datFileReferenceAppIds).toBeUndefined();
  });
});

describe("useKrlTrajectory — isKrlSrcFile", () => {
  it("accepts .src and .krl (case-insensitive)", () => {
    expect(isKrlSrcFile("weld.src")).toBe(true);
    expect(isKrlSrcFile("weld.SRC")).toBe(true);
    expect(isKrlSrcFile("weld.krl")).toBe(true);
    expect(isKrlSrcFile("weld.KRL")).toBe(true);
  });

  it("rejects other extensions and nullish input", () => {
    expect(isKrlSrcFile("weld.dat")).toBe(false);
    expect(isKrlSrcFile("robot.urdf")).toBe(false);
    expect(isKrlSrcFile("")).toBe(false);
    expect(isKrlSrcFile(null)).toBe(false);
    expect(isKrlSrcFile(undefined)).toBe(false);
  });
});

describe("useKrlTrajectory — defaultTrajectoryNameFor", () => {
  it("strips a trailing .src / .krl extension and appends a suffix", () => {
    expect(defaultTrajectoryNameFor("weld_seam.src")).toBe("weld_seam — trajectory");
    expect(defaultTrajectoryNameFor("path.KRL")).toBe("path — trajectory");
  });

  it("falls back to a generic label for empty input", () => {
    expect(defaultTrajectoryNameFor("")).toBe("KRL trajectory");
    expect(defaultTrajectoryNameFor(null)).toBe("KRL trajectory");
  });
});

describe("useKrlTrajectory — findKrlTrajectoryTemplateAppId", () => {
  it("returns the template appId from the mapping back-annotation", () => {
    const found = findKrlTrajectoryTemplateAppId([
      { propertyIRI: "urn:shepard:other", valueName: "x" },
      { propertyIRI: KRL_TEMPLATE_PREDICATE, valueName: "tmpl-77" },
    ]);
    expect(found).toBe("tmpl-77");
  });

  it("returns null when no mapping annotation is present", () => {
    expect(findKrlTrajectoryTemplateAppId([{ propertyIRI: "urn:shepard:urdf:x" }])).toBeNull();
  });

  it("returns null for empty / nullish input", () => {
    expect(findKrlTrajectoryTemplateAppId([])).toBeNull();
    expect(findKrlTrajectoryTemplateAppId(null)).toBeNull();
    expect(findKrlTrajectoryTemplateAppId(undefined)).toBeNull();
  });

  it("ignores a blank valueName", () => {
    expect(
      findKrlTrajectoryTemplateAppId([{ propertyIRI: KRL_TEMPLATE_PREDICATE, valueName: "  " }]),
    ).toBeNull();
  });
});
