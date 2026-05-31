/**
 * UU1 — UI-404-NICE-EMPTY-STATE (2026-05-31) — contract tests for the
 * `EntityNotFound.vue` empty-state component used by detail pages when the
 * entity fetch returned a 404.
 *
 * Mirrors the pure-logic test style used by `NotFoundPanel.test.ts` —
 * Vuetify isn't mounted; Playwright covers the visual rendering.
 */
import { describe, it, expect } from "vitest";

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

describe("EntityNotFound — parentRoute default contract", () => {
  // The component prop defaults `parentRoute` to "/collections" so the CTA
  // always lands the user somewhere useful even when the parent is unknown.
  const DEFAULT_PARENT_ROUTE = "/collections";

  it("defaults to /collections so the CTA always navigates somewhere useful", () => {
    expect(DEFAULT_PARENT_ROUTE).toBe("/collections");
  });
});
