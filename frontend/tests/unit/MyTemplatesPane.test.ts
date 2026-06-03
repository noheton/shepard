/**
 * TPL-ME-BROWSE-1 — pure-logic tests for the MyTemplatesPane filter +
 * shippedVia heuristic. Mirrors the pattern used by
 * `DataObjectNotebooksPane.test.ts`: test the logic units rather than
 * mount the component, because the Vuetify + Nuxt rendering chain
 * needs the full app context.
 *
 * TPL-ME-USE-FROM-BROWSE — additional logic tests for the Collection
 * picker + instantiate flow (confirmInstantiate logic unit).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ShepardTemplateIO } from "@dlr-shepard/backend-client";

// ── Logic units (mirror the inline functions in MyTemplatesPane.vue) ──

function shippedVia(t: ShepardTemplateIO): string {
  const tags = t.tags ?? [];
  if (tags.some(tag => tag.startsWith("system:") || tag === "system")) return "system";
  if (tags.some(tag => tag.startsWith("git:") || tag === "git")) return "git import";
  return "admin upload";
}

function filterTemplates(
  templates: ShepardTemplateIO[],
  q: string,
): ShepardTemplateIO[] {
  const needle = q.trim().toLowerCase();
  if (!needle) return templates;
  return templates.filter(
    t =>
      (t.name ?? "").toLowerCase().includes(needle) ||
      (t.description ?? "").toLowerCase().includes(needle) ||
      (t.templateKind ?? "").toLowerCase().includes(needle) ||
      (t.tags ?? []).some(tag => tag.toLowerCase().includes(needle)),
  );
}

const mk = (overrides: Partial<ShepardTemplateIO>): ShepardTemplateIO => ({
  appId: "00000000-0000-7000-8000-000000000001",
  name: "default",
  templateKind: "DATAOBJECT_RECIPE",
  version: 1,
  body: "{}",
  retired: false,
  ...overrides,
});

describe("MyTemplatesPane — shippedVia heuristic (TPL-ME-BROWSE-1)", () => {
  it("returns 'system' for a system-tagged template", () => {
    expect(shippedVia(mk({ tags: ["system:bootstrap"] }))).toBe("system");
    expect(shippedVia(mk({ tags: ["system"] }))).toBe("system");
  });

  it("returns 'git import' for a git-tagged template", () => {
    expect(shippedVia(mk({ tags: ["git:github.com/dlr-shepard/templates"] }))).toBe(
      "git import",
    );
    expect(shippedVia(mk({ tags: ["git"] }))).toBe("git import");
  });

  it("returns 'admin upload' as the default", () => {
    expect(shippedVia(mk({ tags: [] }))).toBe("admin upload");
    expect(shippedVia(mk({ tags: ["lumen", "tutorial"] }))).toBe("admin upload");
    expect(shippedVia(mk({}))).toBe("admin upload");
  });

  it("prefers system over git when both tags present", () => {
    expect(shippedVia(mk({ tags: ["git:src", "system:bootstrap"] }))).toBe("system");
  });
});

describe("MyTemplatesPane — filter (TPL-ME-BROWSE-1)", () => {
  const corpus = [
    mk({ name: "LUMEN hotfire run", tags: ["lumen", "rocket"] }),
    mk({
      name: "MFFD AFP step",
      description: "Layup process step recipe",
      templateKind: "COLLECTION_RECIPE",
    }),
    mk({ name: "Generic recipe", tags: ["system:bootstrap"] }),
  ];

  it("returns everything on empty query", () => {
    expect(filterTemplates(corpus, "").length).toBe(3);
    expect(filterTemplates(corpus, "   ").length).toBe(3);
  });

  it("matches case-insensitively against name", () => {
    expect(filterTemplates(corpus, "lumen").map(t => t.name)).toEqual([
      "LUMEN hotfire run",
    ]);
  });

  it("matches against description", () => {
    expect(filterTemplates(corpus, "layup").map(t => t.name)).toEqual([
      "MFFD AFP step",
    ]);
  });

  it("matches against templateKind", () => {
    expect(filterTemplates(corpus, "collection_recipe").map(t => t.name)).toEqual([
      "MFFD AFP step",
    ]);
  });

  it("matches against tags", () => {
    expect(filterTemplates(corpus, "rocket").map(t => t.name)).toEqual([
      "LUMEN hotfire run",
    ]);
    expect(filterTemplates(corpus, "system:bootstrap").map(t => t.name)).toEqual([
      "Generic recipe",
    ]);
  });

  it("returns empty when no match", () => {
    expect(filterTemplates(corpus, "no-such-thing")).toEqual([]);
  });
});

// ── TPL-ME-USE-FROM-BROWSE — Collection picker + instantiate logic ───────────
//
// We inline the confirmInstantiate logic for isolated unit testing, mirroring
// the approach used by DataObjectAutocomplete.test.ts. The component mounting
// itself is covered by Playwright end-to-end at 4K viewport.

interface CollectionSearchResult {
  collectionId: number;
  collectionName: string;
}

interface AutoCompleteItem {
  title: string;
  value: CollectionSearchResult;
}

interface DataObject {
  id: number;
  name: string;
  collectionId: number;
}

/**
 * Inlined logic from confirmInstantiate — isolates the unit under test
 * from Nuxt / Vuetify context.
 */
async function confirmInstantiateImpl(params: {
  selectedTemplate: ShepardTemplateIO | null;
  selectedCollection: AutoCompleteItem | undefined;
  instantiateDataObject: (args: {
    collectionAppId: string;
    templateAppId: string;
  }) => Promise<DataObject>;
  navigate: (path: string) => void;
  onSuccess: (msg: string) => void;
}): Promise<{ error: string | null }> {
  const { selectedTemplate, selectedCollection, instantiateDataObject, navigate, onSuccess } =
    params;
  if (!selectedTemplate || !selectedCollection) {
    return { error: "No template or collection selected" };
  }
  const collectionAppId = String(selectedCollection.value.collectionId);
  const templateAppId = selectedTemplate.appId;
  try {
    const created = await instantiateDataObject({ collectionAppId, templateAppId });
    onSuccess(`Created "${created.name}" from template "${selectedTemplate.name}"`);
    navigate(
      `/collections/${selectedCollection.value.collectionId}/dataobjects/${created.id}`,
    );
    return { error: null };
  } catch (err) {
    return {
      error: (err as { message?: string })?.message ?? "Failed to create DataObject from template.",
    };
  }
}

describe("MyTemplatesPane — confirmInstantiate logic (TPL-ME-USE-FROM-BROWSE)", () => {
  const template = mk({
    appId: "00000000-0000-7000-8000-aabbccddee01",
    name: "LUMEN hotfire run",
  });

  const collection: AutoCompleteItem = {
    title: "My LUMEN Collection (ID: 42)",
    value: { collectionId: 42, collectionName: "My LUMEN Collection" },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls instantiateDataObject with correct collectionAppId and templateAppId", async () => {
    const instantiateDataObject = vi.fn().mockResolvedValue({
      id: 999,
      name: "LUMEN hotfire run (instance)",
      collectionId: 42,
    });
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    await confirmInstantiateImpl({
      selectedTemplate: template,
      selectedCollection: collection,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(instantiateDataObject).toHaveBeenCalledOnce();
    expect(instantiateDataObject).toHaveBeenCalledWith({
      collectionAppId: "42",
      templateAppId: "00000000-0000-7000-8000-aabbccddee01",
    });
  });

  it("navigates to the created DataObject on success", async () => {
    const instantiateDataObject = vi.fn().mockResolvedValue({
      id: 999,
      name: "LUMEN hotfire run (instance)",
      collectionId: 42,
    });
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    const result = await confirmInstantiateImpl({
      selectedTemplate: template,
      selectedCollection: collection,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(result.error).toBeNull();
    expect(navigate).toHaveBeenCalledWith("/collections/42/dataobjects/999");
    expect(onSuccess).toHaveBeenCalledWith(
      expect.stringContaining("LUMEN hotfire run"),
    );
  });

  it("returns an error message when the API call fails", async () => {
    const instantiateDataObject = vi.fn().mockRejectedValue(
      new Error("403 Forbidden — write permission required"),
    );
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    const result = await confirmInstantiateImpl({
      selectedTemplate: template,
      selectedCollection: collection,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(result.error).toContain("403 Forbidden");
    expect(navigate).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
  });

  it("returns a generic error message when error has no message field", async () => {
    const instantiateDataObject = vi.fn().mockRejectedValue({});
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    const result = await confirmInstantiateImpl({
      selectedTemplate: template,
      selectedCollection: collection,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(result.error).toBe("Failed to create DataObject from template.");
  });

  it("returns early when no template is selected", async () => {
    const instantiateDataObject = vi.fn();
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    const result = await confirmInstantiateImpl({
      selectedTemplate: null,
      selectedCollection: collection,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(result.error).not.toBeNull();
    expect(instantiateDataObject).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it("returns early when no collection is selected", async () => {
    const instantiateDataObject = vi.fn();
    const navigate = vi.fn();
    const onSuccess = vi.fn();

    const result = await confirmInstantiateImpl({
      selectedTemplate: template,
      selectedCollection: undefined,
      instantiateDataObject,
      navigate,
      onSuccess,
    });

    expect(result.error).not.toBeNull();
    expect(instantiateDataObject).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});

// ── mapToItem helper unit tests ───────────────────────────────────────────────

function mapToItem(r: CollectionSearchResult): AutoCompleteItem {
  return {
    title: `${r.collectionName} (ID: ${r.collectionId})`,
    value: r,
  };
}

describe("MyTemplatesPane — mapToItem (TPL-ME-USE-FROM-BROWSE)", () => {
  it("formats the title with name and id", () => {
    const item = mapToItem({ collectionId: 7, collectionName: "MFFD AFP campaign" });
    expect(item.title).toBe("MFFD AFP campaign (ID: 7)");
    expect(item.value).toEqual({ collectionId: 7, collectionName: "MFFD AFP campaign" });
  });

  it("preserves the original value reference shape", () => {
    const r = { collectionId: 1, collectionName: "LUMEN" };
    const item = mapToItem(r);
    expect(item.value).toStrictEqual(r);
  });
});
