/**
 * II1 (ui-scrutinizer-2026-05-30) — unit tests for the pure
 * IRI → local-name extraction helper used by the
 * `/semantic/predicates/{iri}` heading.
 */
import { describe, it, expect } from "vitest";
import { predicateLocalName, hasUsableLocalName } from "../../utils/predicateLocalName";

describe("predicateLocalName", () => {
  it("extracts the trailing segment from a path-style IRI", () => {
    expect(predicateLocalName("http://schema.org/material")).toBe("material");
    expect(predicateLocalName("https://www.w3.org/2000/01/rdf-schema#label")).toBe("label");
  });

  it("prefers fragment (#) over path (/) when both are present", () => {
    // PROV-O predicates carry both — the fragment is the meaningful local name.
    expect(predicateLocalName("http://www.w3.org/ns/prov#wasInformedBy")).toBe(
      "wasInformedBy",
    );
  });

  it("returns the input unchanged for a URN with no path/fragment separator", () => {
    // urn:shepard:* predicates have no `/` or `#` — already terminal.
    const urn = "urn:shepard:lumen:bench";
    expect(predicateLocalName(urn)).toBe(urn);
  });

  it("handles empty / falsy input gracefully", () => {
    expect(predicateLocalName("")).toBe("");
  });

  it("falls back to input when the IRI ends with the separator (no local segment)", () => {
    // `http://example.org/` has nothing after the slash — return as-is rather
    // than an empty string the page would render as blank.
    expect(predicateLocalName("http://example.org/")).toBe("http://example.org/");
  });

  it("prefers the path local name when the fragment slot is empty", () => {
    // Empty trailing `#` is degenerate — fall through to the path's local name.
    expect(predicateLocalName("http://example.org/foo#")).toBe("foo#");
  });
});

describe("hasUsableLocalName", () => {
  it("is true when the extraction yields a strictly shorter, non-empty token", () => {
    expect(hasUsableLocalName("http://schema.org/material")).toBe(true);
    expect(hasUsableLocalName("http://www.w3.org/ns/prov#wasInformedBy")).toBe(true);
  });

  it("is false for URN-style IRIs (no usable local name)", () => {
    expect(hasUsableLocalName("urn:shepard:lumen:bench")).toBe(false);
  });

  it("is false for empty input", () => {
    expect(hasUsableLocalName("")).toBe(false);
  });
});
