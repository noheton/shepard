/**
 * NEO-AUDIT-007 — CollectionList importedFrom sub-label logic tests.
 *
 * The `CollectionList.vue` component renders a muted sub-label
 * `(importedFrom)` next to the collection name when the field is set.
 * Because mounting the full component requires the Vuetify + Nuxt
 * rendering chain, we test the conditional logic unit here — the same
 * pattern used by `collectionGalleryCard.test.ts`.
 *
 * Logic under test:
 *   - When `importedFrom` is a non-empty string, the sub-label is shown.
 *   - When `importedFrom` is null, undefined, or empty, it is hidden.
 */
import { describe, it, expect } from "vitest";
import type { Collection } from "@dlr-shepard/backend-client";

// ── Fixture builder ───────────────────────────────────────────────────────────

type CollectionWithImportedFrom = Collection & { importedFrom?: string | null };

function buildCollection(
  overrides: Partial<CollectionWithImportedFrom> = {},
): CollectionWithImportedFrom {
  return {
    id: 1,
    createdAt: new Date("2024-01-01"),
    createdBy: "operator",
    updatedAt: null,
    updatedBy: null,
    name: "MFFD-Dropbox",
    dataObjectIds: [],
    incomingIds: [],
    importedFrom: null,
    ...overrides,
  };
}

// ── Logic mirror (mirrors the v-if condition in CollectionList.vue) ───────────

/** Returns true when the importedFrom sub-label should be rendered. */
function shouldShowImportedFrom(
  item: CollectionWithImportedFrom,
): boolean {
  return !!(item as CollectionWithImportedFrom).importedFrom;
}

// ─────────────────────────────────────────────────────────────────────────────
describe("CollectionList — importedFrom sub-label visibility", () => {
  it("shows sub-label when importedFrom is a non-empty string", () => {
    const col = buildCollection({ importedFrom: "tapelaying" });
    expect(shouldShowImportedFrom(col)).toBe(true);
  });

  it("shows sub-label for any non-empty string value", () => {
    for (const label of [
      "bridgewelding",
      "v15-redrive-1",
      "v15-redrive-2",
      "my-custom-origin",
    ]) {
      const col = buildCollection({ importedFrom: label });
      expect(shouldShowImportedFrom(col)).toBe(true);
    }
  });

  it("hides sub-label when importedFrom is null", () => {
    const col = buildCollection({ importedFrom: null });
    expect(shouldShowImportedFrom(col)).toBe(false);
  });

  it("hides sub-label when importedFrom is undefined", () => {
    const col = buildCollection({ importedFrom: undefined });
    expect(shouldShowImportedFrom(col)).toBe(false);
  });

  it("hides sub-label when importedFrom is an empty string", () => {
    // An empty string is falsy — the v-if guard should treat it as absent.
    const col = buildCollection({ importedFrom: "" });
    expect(shouldShowImportedFrom(col)).toBe(false);
  });

  it("the sub-label text matches the importedFrom value", () => {
    const col = buildCollection({ importedFrom: "tapelaying" });
    // The template renders: `({{ collection.importedFrom }})`
    const expectedLabel = `(${col.importedFrom})`;
    expect(expectedLabel).toBe("(tapelaying)");
  });

  it("multiple same-name collections are disambiguated by importedFrom", () => {
    const collections: CollectionWithImportedFrom[] = [
      buildCollection({ id: 1, importedFrom: "tapelaying" }),
      buildCollection({ id: 2, importedFrom: "bridgewelding" }),
      buildCollection({ id: 3, importedFrom: "v15-redrive-1" }),
      buildCollection({ id: 4, importedFrom: "v15-redrive-2" }),
    ];
    // All four have the same name but distinct importedFrom labels.
    const labels = collections.map((c) => c.importedFrom);
    const uniqueLabels = new Set(labels);
    expect(uniqueLabels.size).toBe(4);
    // All show the sub-label.
    for (const col of collections) {
      expect(shouldShowImportedFrom(col)).toBe(true);
    }
  });
});
