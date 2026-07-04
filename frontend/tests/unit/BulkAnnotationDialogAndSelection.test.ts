/**
 * SEMANTIC-ANNOTATE-BULK-UI-1 — unit tests for bulk-annotation selection logic
 * and BulkAnnotationDialog request assembly.
 *
 * Selection state, toggle-all, clear, and page-clear logic are exercised in
 * isolation (pure functions mirroring the component's setup) so we avoid
 * mounting the full Vuetify table in CI.
 */
import { describe, it, expect } from "vitest";

// ─── Selection helpers (mirror of CollectionDataObjectsPanel selection logic) ──

function toggleRow(set: Set<string>, appId: string): Set<string> {
  const next = new Set(set);
  if (next.has(appId)) next.delete(appId);
  else next.add(appId);
  return next;
}

function toggleSelectAll(set: Set<string>, pageIds: string[], select: boolean): Set<string> {
  const next = new Set(set);
  if (select) pageIds.forEach(id => next.add(id));
  else pageIds.forEach(id => next.delete(id));
  return next;
}

function allPageSelected(set: Set<string>, pageIds: string[]): boolean {
  return pageIds.length > 0 && pageIds.every(id => set.has(id));
}

function somePageSelected(set: Set<string>, pageIds: string[]): boolean {
  return pageIds.some(id => set.has(id));
}

// ─── Bulk request assembly (mirror of BulkAnnotationDialog submit logic) ───────

function buildBulkItems(
  subjectAppIds: string[],
  subjectKind: string,
  predicateIri: string,
  predicateLabel: string | null,
  valueIri: string,
  isIri: boolean,
) {
  return subjectAppIds.map(appId => ({
    subjectAppId: appId,
    subjectKind,
    predicateIri,
    predicateLabel,
    ...(isIri ? { objectIri: valueIri } : { objectLiteral: valueIri }),
  }));
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("Selection — toggleRow", () => {
  it("adds an id to an empty set", () => {
    const result = toggleRow(new Set(), "id-1");
    expect(result.has("id-1")).toBe(true);
    expect(result.size).toBe(1);
  });

  it("removes an already-selected id", () => {
    const result = toggleRow(new Set(["id-1", "id-2"]), "id-1");
    expect(result.has("id-1")).toBe(false);
    expect(result.has("id-2")).toBe(true);
  });

  it("does not mutate the original set", () => {
    const original = new Set(["id-1"]);
    toggleRow(original, "id-2");
    expect(original.size).toBe(1);
  });
});

describe("Selection — toggleSelectAll", () => {
  const page = ["id-1", "id-2", "id-3"];

  it("selects all page ids", () => {
    const result = toggleSelectAll(new Set(), page, true);
    expect(result.size).toBe(3);
    page.forEach(id => expect(result.has(id)).toBe(true));
  });

  it("deselects all page ids (cross-page ids stay)", () => {
    const pre = new Set(["id-0", "id-1", "id-2", "id-3"]);
    const result = toggleSelectAll(pre, page, false);
    expect(result.has("id-0")).toBe(true);
    expect(result.has("id-1")).toBe(false);
    expect(result.has("id-3")).toBe(false);
  });

  it("is idempotent — selecting already-selected page does nothing", () => {
    const pre = new Set(page);
    const result = toggleSelectAll(pre, page, true);
    expect(result.size).toBe(3);
  });
});

describe("Selection — allPageSelected / somePageSelected", () => {
  it("allPageSelected: false when nothing selected", () => {
    expect(allPageSelected(new Set(), ["a", "b"])).toBe(false);
  });

  it("allPageSelected: false when only some selected", () => {
    expect(allPageSelected(new Set(["a"]), ["a", "b"])).toBe(false);
  });

  it("allPageSelected: true when all selected", () => {
    expect(allPageSelected(new Set(["a", "b"]), ["a", "b"])).toBe(true);
  });

  it("allPageSelected: false for empty page", () => {
    expect(allPageSelected(new Set(["a"]), [])).toBe(false);
  });

  it("somePageSelected: true when at least one selected", () => {
    expect(somePageSelected(new Set(["a"]), ["a", "b"])).toBe(true);
  });

  it("somePageSelected: false when none selected", () => {
    expect(somePageSelected(new Set(), ["a", "b"])).toBe(false);
  });
});

describe("BulkAnnotationDialog — request assembly", () => {
  const IDS = ["do-id-1", "do-id-2", "do-id-3"];

  it("builds one item per subjectAppId with literal value", () => {
    const items = buildBulkItems(IDS, "DataObject", "urn:test:prop", "My prop", "my value", false);
    expect(items).toHaveLength(3);
    items.forEach((item, i) => {
      expect(item.subjectAppId).toBe(IDS[i]);
      expect(item.subjectKind).toBe("DataObject");
      expect(item.predicateIri).toBe("urn:test:prop");
      expect((item as Record<string, unknown>)["objectLiteral"]).toBe("my value");
      expect(item).not.toHaveProperty("objectIri");
    });
  });

  it("builds items with IRI value when isIri=true", () => {
    const items = buildBulkItems(IDS, "DataObject", "urn:test:prop", null, "https://example.org/term", true);
    items.forEach(item => {
      expect((item as Record<string, unknown>)["objectIri"]).toBe("https://example.org/term");
      expect(item).not.toHaveProperty("objectLiteral");
    });
  });

  it("echoes predicateLabel null when not available", () => {
    const items = buildBulkItems(["x"], "DataObject", "urn:p", null, "v", false);
    expect(items[0]!.predicateLabel).toBeNull();
  });

  it("produces empty array for empty subjectAppIds", () => {
    const items = buildBulkItems([], "DataObject", "urn:p", null, "v", false);
    expect(items).toHaveLength(0);
  });
});
