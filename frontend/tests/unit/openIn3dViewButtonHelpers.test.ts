/**
 * V2CONV-B4-FE — unit tests for the OpenIn3dViewButton eligibility helper.
 * Replaces openInSceneGraphButtonHelpers.test.ts (deleted with the bespoke
 * scene-graph subsystem).
 */
import { describe, it, expect } from "vitest";
import {
  hasSceneGraphRole,
  RDK_PREFIX,
  URDF_PREFIX,
  MAPPING_TEMPLATE_PREDICATE,
} from "../../components/container/file/openIn3dViewButtonHelpers";

describe("openIn3dViewButtonHelpers — hasSceneGraphRole", () => {
  it("is eligible when a mapping-template back-annotation is present", () => {
    expect(
      hasSceneGraphRole([{ propertyIRI: MAPPING_TEMPLATE_PREDICATE }], "x.bin"),
    ).toBe(true);
  });

  it("is eligible on any urn:shepard:rdk:* predicate", () => {
    expect(hasSceneGraphRole([{ propertyIRI: `${RDK_PREFIX}appVersion` }], "x")).toBe(true);
  });

  it("is eligible on any urn:shepard:urdf:* predicate", () => {
    expect(hasSceneGraphRole([{ propertyIRI: `${URDF_PREFIX}role` }], "x")).toBe(true);
  });

  it("is eligible on a .urdf filename fallback", () => {
    expect(hasSceneGraphRole([], "kr210.urdf")).toBe(true);
    expect(hasSceneGraphRole([], "STATION.RDK")).toBe(true);
  });

  it("is not eligible for an unrelated file", () => {
    expect(hasSceneGraphRole([{ propertyIRI: "urn:shepard:other" }], "report.pdf")).toBe(false);
    expect(hasSceneGraphRole(null, null)).toBe(false);
  });
});
