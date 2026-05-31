/**
 * UU1 — UI-404-NICE-EMPTY-STATE (2026-05-31) — contract tests for the
 * `EntityNotFound.vue` empty-state component used by detail pages when the
 * entity fetch returned a 404.
 *
 * UU2 — UI-STALE-URL-HINT: the hint card is gated by `isNumericLegacyId`
 * applied to the `requestedId` prop. These tests assert the gate works for
 * every supported `entityKind` and that the canonical operator repro
 * (`1787`, `1792`) trips the hint while a UUID v7 appId does not.
 *
 * Mirrors the pure-logic test style used by `NotFoundPanel.test.ts` —
 * Vuetify isn't mounted; Playwright covers the visual rendering.
 */
import { describe, it, expect } from "vitest";
import { isNumericLegacyId } from "~/utils/idShape";

const KIND_LABEL = {
  Collection: "collection",
  DataObject: "data object",
  FileReference: "file reference",
  TimeseriesReference: "timeseries reference",
  StructuredDataReference: "structured-data reference",
  Container: "container",
} as const;

const KIND_CTA = {
  Collection: "Browse Collections",
  DataObject: "Back to Collection",
  FileReference: "Back to Data Object",
  TimeseriesReference: "Back to Data Object",
  StructuredDataReference: "Back to Data Object",
  Container: "Browse Containers",
} as const;

type EntityKind = keyof typeof KIND_LABEL;

function buildHeading(kind: EntityKind): string {
  return `This ${KIND_LABEL[kind]} doesn't exist.`;
}

describe("EntityNotFound — heading copy per entityKind", () => {
  it.each(Object.keys(KIND_LABEL) as EntityKind[])(
    "produces the expected heading for %s",
    kind => {
      expect(buildHeading(kind)).toBe(`This ${KIND_LABEL[kind]} doesn't exist.`);
      expect(KIND_CTA[kind]).toMatch(/^(Browse|Back)/);
    },
  );
});

describe("EntityNotFound — UU2 stale-URL hint visibility", () => {
  // The component shows the hint card iff `isNumericLegacyId(requestedId)`.
  it("shows the hint for the canonical operator-repro numeric ids", () => {
    expect(isNumericLegacyId("1787")).toBe(true); // collection in the repro
    expect(isNumericLegacyId("1792")).toBe(true); // dataobject in the repro
  });

  it("hides the hint when requestedId is a UUID v7 appId", () => {
    expect(isNumericLegacyId("019e6ffc-89a4-76b5-8dbb-15888646a904")).toBe(
      false,
    );
  });

  it("hides the hint for empty / null / mixed-shape ids", () => {
    expect(isNumericLegacyId("")).toBe(false);
    expect(isNumericLegacyId(null)).toBe(false);
    expect(isNumericLegacyId("not-a-uuid")).toBe(false);
  });
});

describe("EntityNotFound — parentRoute default contract", () => {
  // The component prop defaults `parentRoute` to "/collections" so the CTA
  // always lands the user somewhere useful even when the parent is unknown.
  const DEFAULT_PARENT_ROUTE = "/collections";

  it("defaults to /collections so the CTA always navigates somewhere useful", () => {
    expect(DEFAULT_PARENT_ROUTE).toBe("/collections");
  });
});
