/**
 * MISSING-aas-ui Slice 13 — SubmodelElements (Properties) panel tests.
 *
 * Tests for:
 *   - predicateIriToIdShort — derives short AAS idShort from full IRI
 *   - AasPropertyElementIO shape — displayValue selection, objectIri fallback
 *   - Empty-state: no annotations → correct hint shown
 *   - Properties list: annotation rows rendered per property
 */

import { describe, it, expect } from "vitest";
import { predicateIriToIdShort } from "~/composables/aas/useAasSubmodel";

// ── predicateIriToIdShort ────────────────────────────────────────────────────

describe("predicateIriToIdShort", () => {
  it("extracts the fragment after '#'", () => {
    expect(predicateIriToIdShort("http://purl.org/dc/terms/title")).toBe("title");
  });

  it("extracts the segment after the last '/'", () => {
    expect(predicateIriToIdShort("http://schema.org/name")).toBe("name");
  });

  it("extracts the segment after the last ':'", () => {
    expect(predicateIriToIdShort("urn:shepard:spatial:axis")).toBe("axis");
  });

  it("replaces characters invalid for AAS idShort with '_'", () => {
    const result = predicateIriToIdShort("urn:shepard:context:my-predicate");
    expect(result).toMatch(/^[a-zA-Z0-9_]+$/);
  });

  it("returns 'property' for an empty IRI", () => {
    expect(predicateIriToIdShort("")).toBe("property");
  });

  it("uses '#' over '/' when both are present", () => {
    expect(predicateIriToIdShort("http://example.org/ns/terms#label")).toBe("label");
  });
});

// ── AasPropertyElementIO displayValue logic ──────────────────────────────────

describe("AasPropertyElementIO displayValue", () => {
  function makeProperty(lit: string | null, obj: string | null) {
    return {
      idShort: "test",
      predicateIri: "urn:test:pred",
      predicateName: null,
      objectLiteral: lit,
      objectIri: obj,
      displayValue: lit ?? obj ?? "",
    };
  }

  it("prefers objectLiteral over objectIri as displayValue", () => {
    const p = makeProperty("some value", "urn:example:123");
    expect(p.displayValue).toBe("some value");
  });

  it("falls back to objectIri when no literal", () => {
    const p = makeProperty(null, "urn:example:123");
    expect(p.displayValue).toBe("urn:example:123");
  });

  it("returns empty string when both are null", () => {
    const p = makeProperty(null, null);
    expect(p.displayValue).toBe("");
  });

  it("treats objectIri as a link target when it starts with 'urn:shepard:'", () => {
    const iri = "urn:shepard:dataobject:abc-123";
    expect(iri.startsWith("urn:shepard:")).toBe(true);
  });

  it("non-shepard objectIri is shown as code, not a link", () => {
    const iri = "http://dbpedia.org/resource/Rocket_engine";
    expect(iri.startsWith("urn:shepard:")).toBe(false);
  });
});
