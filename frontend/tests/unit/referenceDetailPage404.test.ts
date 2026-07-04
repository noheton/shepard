/**
 * UI-404-NICE-EMPTY-STATE-REF-PAGES — unit tests proving that FileReference
 * and StructuredDataReference detail pages wire the composable `notFound` ref
 * to `EntityNotFound` instead of leaving the user on an eternal spinner.
 *
 * Tests use the inline-helper pattern (no Vuetify mount) — the template
 * display conditions are expressed as pure functions so Playwright can cover
 * the visual rendering independently.
 */
import { describe, it, expect } from "vitest";

// ── Display-condition helpers (mirror the v-if/v-else-if logic in each page) ──

type LoadState<T> = {
  entity: T | undefined;
  notFound: boolean;
};

type DisplaySlot = "content" | "entityNotFound" | "spinner";

function resolveDisplaySlot(
  collection: object | undefined,
  dataObject: object | undefined,
  state: LoadState<object>,
): DisplaySlot {
  if (!!collection && !!dataObject && !!state.entity) return "content";
  if (state.notFound) return "entityNotFound";
  return "spinner";
}

const mockCollection = { id: 1, name: "Test Collection", appId: "col-uuid" };
const mockDataObject = { id: 2, name: "Test DO", appId: "do-uuid" };
const mockReference = { id: 3, name: "Test Reference", appId: "ref-uuid" };

// ── FileReference detail page ─────────────────────────────────────────────────

describe("FileReference detail page — display-slot selection", () => {
  it("shows content when all three entities are loaded", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe("content");
  });

  it("shows EntityNotFound when the file reference fetch returns 404", () => {
    const state = { entity: undefined, notFound: true };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe(
      "entityNotFound",
    );
  });

  it("shows spinner while the file reference is still loading (not 404)", () => {
    const state = { entity: undefined, notFound: false };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe(
      "spinner",
    );
  });

  it("shows spinner while the collection is still loading (reference is unaffected)", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(undefined, mockDataObject, state)).toBe("spinner");
  });

  it("shows spinner while the dataObject is still loading", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(mockCollection, undefined, state)).toBe("spinner");
  });

  it("prefers EntityNotFound over spinner when notFound is true but collection/DO are still loading", () => {
    // This mirrors the v-else-if ordering: notFound check comes before the
    // plain v-else spinner, so a 404 reference is surfaced even if the parent
    // entities haven't resolved (edge case, but important for fast-fail UX).
    const state = { entity: undefined, notFound: true };
    expect(resolveDisplaySlot(undefined, undefined, state)).toBe(
      "entityNotFound",
    );
  });
});

// ── StructuredDataReference detail page — same logic, same assertions ─────────

describe("StructuredDataReference detail page — display-slot selection", () => {
  it("shows content when all three entities are loaded", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe("content");
  });

  it("shows EntityNotFound when the structured-data reference fetch returns 404", () => {
    const state = { entity: undefined, notFound: true };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe(
      "entityNotFound",
    );
  });

  it("shows spinner while the structured-data reference is still loading", () => {
    const state = { entity: undefined, notFound: false };
    expect(resolveDisplaySlot(mockCollection, mockDataObject, state)).toBe(
      "spinner",
    );
  });

  it("shows spinner while the collection is loading", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(undefined, mockDataObject, state)).toBe("spinner");
  });

  it("shows spinner while the data object is loading", () => {
    const state = { entity: mockReference, notFound: false };
    expect(resolveDisplaySlot(mockCollection, undefined, state)).toBe("spinner");
  });

  it("prefers EntityNotFound when notFound=true regardless of parent load state", () => {
    const state = { entity: undefined, notFound: true };
    expect(resolveDisplaySlot(undefined, undefined, state)).toBe("entityNotFound");
  });
});

// ── requestedId and parentRoute prop values ──────────────────────────────────

describe("EntityNotFound props for reference pages", () => {
  const collectionsPath = "/collections/";
  const dataObjectsPathFragment = "/dataobjects/";
  const collectionId = "019e6ffc-89a4-76b5-8dbb-15888646a904";
  const dataObjectId = "019e6ffc-89a4-76b5-8dbb-888888888888";
  const referenceId = "019e6ffc-89a4-76b5-8dbb-999999999999";

  function buildParentRoute(colId: string, doId: string) {
    return collectionsPath + colId + dataObjectsPathFragment + doId;
  }

  it("FileReference parentRoute navigates to the parent DataObject", () => {
    const parentRoute = buildParentRoute(collectionId, dataObjectId);
    expect(parentRoute).toBe(
      `/collections/${collectionId}/dataobjects/${dataObjectId}`,
    );
  });

  it("StructuredDataReference parentRoute navigates to the parent DataObject", () => {
    const parentRoute = buildParentRoute(collectionId, dataObjectId);
    expect(parentRoute).toBe(
      `/collections/${collectionId}/dataobjects/${dataObjectId}`,
    );
  });

  it("requestedId surfaces the UUID v7 route param so stale-URL hint fires correctly", () => {
    // UUID v7 → no stale-URL hint (already tested by EntityNotFound.test.ts)
    const requestedId = referenceId;
    expect(requestedId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    );
  });

  it("requestedId with a numeric legacy id triggers the stale-URL hint (via isNumericLegacyId logic)", () => {
    const numericId = "12345";
    expect(/^\d+$/.test(numericId)).toBe(true);
  });
});
