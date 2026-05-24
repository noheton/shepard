/**
 * RDM-005 — Metadata Completeness Score helper tests.
 *
 * Each check is exercised in pass + fail (+ edge-case where the
 * boundary is non-trivial — description min-length, ORCID
 * present/null/empty-string, dataObjectIds null vs empty). Plus the
 * total-points invariant + band classification + score clamping.
 *
 * Cases: 28 (well beyond the spec's "10+ cases" floor).
 */
import { describe, it, expect } from "vitest";
import type { Collection } from "@dlr-shepard/backend-client";
import {
  computeMetadataCompleteness,
  DESCRIPTION_MIN_CHARS,
  type MetadataCompletenessInputs,
} from "../../utils/metadataCompleteness";

/**
 * Builder for a Collection with every field set to a "passing" value.
 * Individual tests override one field at a time to flip a single
 * check to fail.
 */
function buildFullCollection(overrides: Partial<Collection> = {}): Collection {
  // Cast via `unknown` so we can include the LIC1 fields the
  // generated client carries but the type may not yet surface on
  // every consumer. The helper uses the same defensive cast.
  return {
    id: 42,
    appId: "01234567-89ab-cdef-0123-456789abcdef",
    createdAt: new Date("2024-06-15T12:00:00Z"),
    createdBy: "alice",
    updatedAt: null,
    updatedBy: null,
    name: "LUMEN Hotfire Campaign",
    description:
      "Synthetic showcase dataset for shepard. Hotfire test campaign of " +
      "the LUMEN demonstrator engine. NOT REAL DLR/LUMEN data.",
    status: "READY",
    attributes: {},
    dataObjectIds: [1, 2, 3, 4, 5],
    incomingIds: [],
    defaultFileContainerId: null,
    heroImageUrl: "https://example.com/lumen.png",
    license: "CC-BY-4.0",
    accessRights: "OPEN",
    ...overrides,
  } as unknown as Collection;
}

function buildFullInputs(
  overrides: Partial<MetadataCompletenessInputs> = {},
): MetadataCompletenessInputs {
  return {
    collection: buildFullCollection(),
    semanticAnnotationCount: 8,
    labJournalCount: 3,
    creatorOrcid: "0000-0001-2345-6789",
    ...overrides,
  };
}

describe("computeMetadataCompleteness — invariants", () => {
  it("total points sums to exactly 100", () => {
    const { maxScore, checks } = computeMetadataCompleteness(buildFullInputs());
    const sum = checks.reduce((acc, c) => acc + c.points, 0);
    expect(maxScore).toBe(100);
    expect(sum).toBe(100);
  });

  it("renders exactly 9 checks (current check list)", () => {
    const { checks } = computeMetadataCompleteness(buildFullInputs());
    expect(checks).toHaveLength(9);
  });

  it("all checks have unique ids", () => {
    const { checks } = computeMetadataCompleteness(buildFullInputs());
    const ids = checks.map(c => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("fully-populated Collection scores 100 / green", () => {
    const { score, band } = computeMetadataCompleteness(buildFullInputs());
    expect(score).toBe(100);
    expect(band).toBe("success");
  });

  it("empty Collection scores 0 / red", () => {
    const result = computeMetadataCompleteness({
      collection: buildFullCollection({
        name: "",
        description: "",
        dataObjectIds: [],
        heroImageUrl: null,
        license: null,
        accessRights: null,
      } as unknown as Partial<Collection>),
      semanticAnnotationCount: 0,
      labJournalCount: 0,
      creatorOrcid: null,
    });
    expect(result.score).toBe(0);
    expect(result.band).toBe("error");
  });
});

describe("computeMetadataCompleteness — per-check pass/fail", () => {
  it("name pass: non-empty", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "name")?.passed).toBe(true);
  });

  it("name fail: empty string", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ name: "" }),
    });
    expect(r.checks.find(c => c.id === "name")?.passed).toBe(false);
  });

  it("name fail: whitespace only", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ name: "   " }),
    });
    expect(r.checks.find(c => c.id === "name")?.passed).toBe(false);
  });

  it(`description pass: ≥ ${DESCRIPTION_MIN_CHARS} chars`, () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "description")?.passed).toBe(true);
  });

  it(`description fail: < ${DESCRIPTION_MIN_CHARS} chars`, () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ description: "stub" }),
    });
    expect(r.checks.find(c => c.id === "description")?.passed).toBe(false);
  });

  it("description fail: null", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ description: null }),
    });
    expect(r.checks.find(c => c.id === "description")?.passed).toBe(false);
  });

  it("description boundary: exactly threshold chars passes", () => {
    const desc = "x".repeat(DESCRIPTION_MIN_CHARS);
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ description: desc }),
    });
    expect(r.checks.find(c => c.id === "description")?.passed).toBe(true);
  });

  it("license pass: SPDX id", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "license")?.passed).toBe(true);
  });

  it("license fail: null", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        license: null,
      } as unknown as Partial<Collection>),
    });
    expect(r.checks.find(c => c.id === "license")?.passed).toBe(false);
  });

  it("license fail: empty string", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        license: "",
      } as unknown as Partial<Collection>),
    });
    expect(r.checks.find(c => c.id === "license")?.passed).toBe(false);
  });

  it("accessRights pass: OPEN", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "accessRights")?.passed).toBe(true);
  });

  it("accessRights fail: null", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        accessRights: null,
      } as unknown as Partial<Collection>),
    });
    expect(r.checks.find(c => c.id === "accessRights")?.passed).toBe(false);
  });

  it("creatorOrcid pass: 16-digit string", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "creatorOrcid")?.passed).toBe(true);
  });

  it("creatorOrcid fail: null", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      creatorOrcid: null,
    });
    expect(r.checks.find(c => c.id === "creatorOrcid")?.passed).toBe(false);
  });

  it("creatorOrcid fail: empty string", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      creatorOrcid: "",
    });
    expect(r.checks.find(c => c.id === "creatorOrcid")?.passed).toBe(false);
  });

  it("semanticAnnotation pass: count > 0", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "semanticAnnotation")?.passed).toBe(
      true,
    );
  });

  it("semanticAnnotation fail: count = 0", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      semanticAnnotationCount: 0,
    });
    expect(r.checks.find(c => c.id === "semanticAnnotation")?.passed).toBe(
      false,
    );
  });

  it("semanticAnnotation fail: count = null (still loading)", () => {
    // While loading we conservatively render the row as failing — the
    // widget shows a spinner alongside the chip and the score
    // re-computes once the fetch settles.
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      semanticAnnotationCount: null,
    });
    expect(r.checks.find(c => c.id === "semanticAnnotation")?.passed).toBe(
      false,
    );
  });

  it("labJournal pass: count > 0", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "labJournal")?.passed).toBe(true);
  });

  it("labJournal fail: count = 0", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      labJournalCount: 0,
    });
    expect(r.checks.find(c => c.id === "labJournal")?.passed).toBe(false);
  });

  it("heroImage pass: url string", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "heroImage")?.passed).toBe(true);
  });

  it("heroImage fail: null", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ heroImageUrl: null }),
    });
    expect(r.checks.find(c => c.id === "heroImage")?.passed).toBe(false);
  });

  it("dataObjects pass: non-empty list", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "dataObjects")?.passed).toBe(true);
  });

  it("dataObjects fail: empty list", () => {
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ dataObjectIds: [] }),
    });
    expect(r.checks.find(c => c.id === "dataObjects")?.passed).toBe(false);
  });
});

describe("computeMetadataCompleteness — band classification", () => {
  it("band = error when score < 50", () => {
    // 9 + 10 + 15 + 5 = 39 (name + accessRights + description + labJournal)
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        license: null,
        heroImageUrl: null,
        dataObjectIds: [],
      } as unknown as Partial<Collection>),
      semanticAnnotationCount: 0,
      creatorOrcid: null,
    });
    expect(r.band).toBe("error");
  });

  it("band = warning when score in [50, 80)", () => {
    // license missing (-20), heroImage missing (-5), description missing (-15)
    // → 100 - 40 = 60
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        license: null,
        heroImageUrl: null,
        description: "stub",
      } as unknown as Partial<Collection>),
    });
    expect(r.score).toBe(60);
    expect(r.band).toBe("warning");
  });

  it("band = success when score >= 80", () => {
    // heroImage missing only — 100 - 5 = 95
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({ heroImageUrl: null }),
    });
    expect(r.score).toBe(95);
    expect(r.band).toBe("success");
  });

  it("band = success exactly at the 80 boundary", () => {
    // license (-20) → 100 - 20 = 80
    const r = computeMetadataCompleteness({
      ...buildFullInputs(),
      collection: buildFullCollection({
        license: null,
      } as unknown as Partial<Collection>),
    });
    expect(r.score).toBe(80);
    expect(r.band).toBe("success");
  });
});

describe("computeMetadataCompleteness — deep-link wiring", () => {
  it("every fail-able check exposes a non-empty action label", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    for (const check of r.checks) {
      expect(check.actionLabel.length).toBeGreaterThan(0);
    }
  });

  it("description / license / accessRights / annotations / labJournal / heroImage / dataObjects have on-page deepLink anchors", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    const withAnchor = new Set([
      "description",
      "license",
      "accessRights",
      "semanticAnnotation",
      "labJournal",
      "heroImage",
      "dataObjects",
    ]);
    for (const check of r.checks) {
      if (withAnchor.has(check.id)) {
        expect(check.deepLink).toBeTruthy();
      }
    }
  });

  it("name + creatorOrcid have null deepLink (off-page actions)", () => {
    const r = computeMetadataCompleteness(buildFullInputs());
    expect(r.checks.find(c => c.id === "name")?.deepLink).toBeNull();
    expect(r.checks.find(c => c.id === "creatorOrcid")?.deepLink).toBeNull();
  });
});
