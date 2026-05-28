/**
 * REF-EDIT-TPL-6 — unit tests for URIReference template-driven create prefill.
 *
 * The Add Relationship dialog reads a `urn:shepard:reference:uriRelationship`
 * annotation off the parent DataObject and seeds the URI sub-form's default
 * relationship label and URI placeholder. The hint never overwrites a value
 * the user has already typed.
 *
 * Pattern: pure-helper tests (matches `EditFileReferenceDialog.test.ts` and
 * the sister TPL-3 file). The existing harness does not mount Vue components.
 */
import { describe, it, expect } from "vitest";
import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import {
  REFERENCE_PREDICATE,
  findAnnotationByPredicate,
  parseUriRelationshipHint,
} from "~/composables/references/referenceTemplatePrefill";

// ── Helpers ──────────────────────────────────────────────────────────────────

function mkAnn(
  propertyIRI: string,
  valueName: string | null,
): SemanticAnnotation {
  return {
    propertyName: "",
    propertyIRI,
    valueName: valueName ?? undefined,
    valueIRI: undefined,
  } as unknown as SemanticAnnotation;
}

/**
 * Simulates the URI-input default-relationship seeding flow.
 * Returns the relationship label the input would carry after the handler ran.
 */
function simulateRelationshipSeedOnOpen(opts: {
  currentValue: string;
  annotations: SemanticAnnotation[] | null;
}): string {
  const { currentValue, annotations } = opts;
  // Legacy default is "URI"; treat empty + "URI" as "not user-edited".
  const userTouched =
    currentValue.trim().length > 0 && currentValue.trim() !== "URI";
  if (userTouched) return currentValue;
  const annotation = findAnnotationByPredicate(
    annotations,
    REFERENCE_PREDICATE.URI_RELATIONSHIP,
  );
  const hint = parseUriRelationshipHint(annotation);
  if (!hint?.relationship) return currentValue;
  return hint.relationship;
}

/** Simulates the URI placeholder derivation. */
function simulatePlaceholderOnOpen(
  annotations: SemanticAnnotation[] | null,
): string | undefined {
  const annotation = findAnnotationByPredicate(
    annotations,
    REFERENCE_PREDICATE.URI_RELATIONSHIP,
  );
  const hint = parseUriRelationshipHint(annotation);
  return hint?.uriPrefix;
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe("parseUriRelationshipHint", () => {
  it("returns null for null annotation", () => {
    expect(parseUriRelationshipHint(null)).toBeNull();
  });

  it("returns null when valueName is missing", () => {
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, null),
      ),
    ).toBeNull();
  });

  it("returns null when valueName is empty", () => {
    expect(
      parseUriRelationshipHint(mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "")),
    ).toBeNull();
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "   "),
      ),
    ).toBeNull();
  });

  it("parses a JSON-object value with both fields", () => {
    const value = JSON.stringify({
      relationship: "prov:wasDerivedFrom",
      uriPrefix: "https://doi.org/",
    });
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, value),
      ),
    ).toEqual({
      relationship: "prov:wasDerivedFrom",
      uriPrefix: "https://doi.org/",
    });
  });

  it("parses a JSON-object value with only relationship", () => {
    const value = JSON.stringify({ relationship: "cito:isCitedBy" });
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, value),
      ),
    ).toEqual({ relationship: "cito:isCitedBy", uriPrefix: undefined });
  });

  it("parses a JSON-object value with only uriPrefix", () => {
    const value = JSON.stringify({ uriPrefix: "https://orcid.org/" });
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, value),
      ),
    ).toEqual({ relationship: undefined, uriPrefix: "https://orcid.org/" });
  });

  it("treats a plain string as the relationship label", () => {
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "m4i:hasMethodPlan"),
      ),
    ).toEqual({ relationship: "m4i:hasMethodPlan" });
  });

  it("falls back to plain-string treatment when JSON is malformed", () => {
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "{ not real json"),
      ),
    ).toEqual({ relationship: "{ not real json" });
  });

  it("returns null when JSON parses but carries neither field", () => {
    const value = JSON.stringify({ other: "value" });
    expect(
      parseUriRelationshipHint(
        mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, value),
      ),
    ).toBeNull();
  });
});

describe("simulateRelationshipSeedOnOpen — relationship default flow", () => {
  it("seeds the relationship from the hint when current is empty", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "prov:wasDerivedFrom"),
    ];
    expect(
      simulateRelationshipSeedOnOpen({ currentValue: "", annotations }),
    ).toBe("prov:wasDerivedFrom");
  });

  it("seeds when current is the legacy default 'URI'", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "cito:isCitedBy"),
    ];
    expect(
      simulateRelationshipSeedOnOpen({ currentValue: "URI", annotations }),
    ).toBe("cito:isCitedBy");
  });

  it("does NOT overwrite a user-typed relationship", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "prov:wasDerivedFrom"),
    ];
    expect(
      simulateRelationshipSeedOnOpen({
        currentValue: "my custom relation",
        annotations,
      }),
    ).toBe("my custom relation");
  });

  it("leaves the current value alone when no hint is present", () => {
    expect(
      simulateRelationshipSeedOnOpen({ currentValue: "URI", annotations: [] }),
    ).toBe("URI");
  });

  it("ignores annotations on a different predicate", () => {
    const annotations = [mkAnn("urn:shepard:other:thing", "ignored")];
    expect(
      simulateRelationshipSeedOnOpen({ currentValue: "URI", annotations }),
    ).toBe("URI");
  });
});

describe("simulatePlaceholderOnOpen — uri placeholder flow", () => {
  it("returns the uriPrefix from a JSON-object hint", () => {
    const value = JSON.stringify({
      relationship: "cito:isCitedBy",
      uriPrefix: "https://doi.org/",
    });
    const annotations = [mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, value)];
    expect(simulatePlaceholderOnOpen(annotations)).toBe("https://doi.org/");
  });

  it("returns undefined when hint carries only a relationship string", () => {
    const annotations = [
      mkAnn(REFERENCE_PREDICATE.URI_RELATIONSHIP, "prov:wasDerivedFrom"),
    ];
    expect(simulatePlaceholderOnOpen(annotations)).toBeUndefined();
  });

  it("returns undefined when no annotation is present", () => {
    expect(simulatePlaceholderOnOpen([])).toBeUndefined();
    expect(simulatePlaceholderOnOpen(null)).toBeUndefined();
  });
});
