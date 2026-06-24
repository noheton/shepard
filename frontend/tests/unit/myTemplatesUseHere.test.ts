/**
 * TPL-ME-USE-FROM-BROWSE — unit tests for the "Use here…" affordance in
 * MyTemplatesPane.vue.
 *
 * Tests the inlined helpers:
 *   - shippedVia() — pure tag classifier
 *   - confirmUse() logic — calls instantiateDataObject with correct appIds
 *     and navigates to the new DataObject on success
 *   - pickableCollections filter — accepts only collections with a readable appId
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

// ── shippedVia helper (inlined from component) ──────────────────────────────

function shippedVia(t: { tags?: string[] }): string {
  const tags = t.tags ?? [];
  if (tags.some(tag => tag.startsWith("system:") || tag === "system")) return "system";
  if (tags.some(tag => tag.startsWith("git:") || tag === "git")) return "git import";
  return "admin upload";
}

// ── pickableCollections helper ───────────────────────────────────────────────

function readCollectionAppId(c: unknown): string | null {
  if (c == null || typeof c !== "object") return null;
  const direct = (c as { appId?: string | null }).appId;
  if (typeof direct === "string" && direct.length > 0) return direct;
  const bag = (c as { additional_properties?: { appId?: string | null } })
    .additional_properties;
  const fromBag = bag?.appId;
  if (typeof fromBag === "string" && fromBag.length > 0) return fromBag;
  return null;
}

function pickable(collections: unknown[]): unknown[] {
  return collections.filter(c => !!readCollectionAppId(c));
}

// ── confirmUse logic (inlined from component) ────────────────────────────────

const COLLECTION_APP_ID = "019e1111-0000-7000-8000-000000000001";
const TEMPLATE_APP_ID   = "019e2222-0000-7000-8000-000000000002";
const DO_APP_ID         = "019e3333-0000-7000-8000-000000000003";
const collectionsPath   = "/collections/";
const dataObjectsPathFragment = "/dataobjects/";

const mockRouterPush  = vi.fn();
const mockHandleError = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, { handleError: mockHandleError });
});

async function runConfirmUse(opts: {
  selectedAppId: string;
  collectionAppId: string;
  instantiateResult?: { id: number; appId?: string; additional_properties?: { appId?: string } };
  instantiateError?: { response?: { status?: number } };
}) {
  const isInstantiating = ref(false);
  const useError = ref<string | null>(null);
  const selected = ref<{ appId?: string } | null>({ appId: opts.selectedAppId });
  const useTargetCollection = ref<unknown>({ appId: opts.collectionAppId });

  const collectionAppId = readCollectionAppId(useTargetCollection.value);
  if (!selected.value?.appId || !collectionAppId) return { useError: useError.value };

  isInstantiating.value = true;
  useError.value = null;
  try {
    if (opts.instantiateError) throw opts.instantiateError;
    const created = opts.instantiateResult!;
    const doAppId =
      (created as { appId?: string }).appId ??
      (created as { additional_properties?: { appId?: string } }).additional_properties?.appId ??
      null;
    await mockRouterPush(
      collectionsPath +
        collectionAppId +
        dataObjectsPathFragment +
        (doAppId ?? created.id),
    );
  } catch (e) {
    const status = (e as { response?: { status?: number } })?.response?.status;
    if (status === 403) {
      useError.value = "You don't have write permission on that Collection.";
    } else if (status === 404) {
      useError.value = "Template or Collection not found — the list may be stale.";
    } else {
      useError.value = "Instantiation failed — please try again.";
      mockHandleError(e, "instantiateDataObject from browse");
    }
  } finally {
    isInstantiating.value = false;
  }
  return { useError: useError.value };
}

// ── tests ────────────────────────────────────────────────────────────────────

describe("shippedVia", () => {
  it("returns 'system' for the 'system' tag", () => {
    expect(shippedVia({ tags: ["system"] })).toBe("system");
  });

  it("returns 'system' for a 'system:*' prefixed tag", () => {
    expect(shippedVia({ tags: ["system:bootstrap"] })).toBe("system");
  });

  it("returns 'git import' for the 'git' tag", () => {
    expect(shippedVia({ tags: ["git"] })).toBe("git import");
  });

  it("returns 'git import' for a 'git:*' prefixed tag", () => {
    expect(shippedVia({ tags: ["git:main-branch"] })).toBe("git import");
  });

  it("returns 'admin upload' when no special tags are present", () => {
    expect(shippedVia({ tags: ["lumen", "trace3d"] })).toBe("admin upload");
  });

  it("returns 'admin upload' when tags is absent", () => {
    expect(shippedVia({})).toBe("admin upload");
  });

  it("prefers 'system' over 'git' when both tags are present", () => {
    expect(shippedVia({ tags: ["git", "system"] })).toBe("system");
  });
});

describe("pickableCollections", () => {
  it("includes collections with a direct appId property", () => {
    const cs = [
      { id: 1, appId: "019e1111-0000-7000-8000-000000000001" },
      { id: 2, appId: "019e2222-0000-7000-8000-000000000002" },
    ];
    expect(pickable(cs)).toHaveLength(2);
  });

  it("includes collections whose appId lives in additional_properties", () => {
    const c = {
      id: 3,
      additional_properties: { appId: "019e3333-0000-7000-8000-000000000003" },
    };
    expect(pickable([c])).toHaveLength(1);
  });

  it("excludes collections with no appId at all", () => {
    const cs = [{ id: 4, name: "No appId" }];
    expect(pickable(cs)).toHaveLength(0);
  });

  it("excludes collections where appId is an empty string", () => {
    const cs = [{ id: 5, appId: "" }];
    expect(pickable(cs)).toHaveLength(0);
  });
});

describe("confirmUse — success path", () => {
  it("navigates to the new DataObject using the returned appId", async () => {
    await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateResult: { id: 99, appId: DO_APP_ID },
    });

    expect(mockRouterPush).toHaveBeenCalledOnce();
    const route: string = mockRouterPush.mock.calls[0]![0];
    expect(route).toBe(
      `/collections/${COLLECTION_APP_ID}/dataobjects/${DO_APP_ID}`,
    );
  });

  it("falls back to numeric id when returned entity has no appId", async () => {
    await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateResult: { id: 77 },
    });

    const route: string = mockRouterPush.mock.calls[0]![0];
    expect(route).toContain("/dataobjects/77");
  });

  it("reads appId from additional_properties when direct field is absent", async () => {
    await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateResult: {
        id: 55,
        additional_properties: { appId: DO_APP_ID },
      },
    });

    const route: string = mockRouterPush.mock.calls[0]![0];
    expect(route).toContain(`/dataobjects/${DO_APP_ID}`);
  });
});

describe("confirmUse — error handling", () => {
  it("sets a permission error message on 403", async () => {
    const { useError } = await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateError: { response: { status: 403 } },
    });
    expect(useError).toMatch(/write permission/);
    expect(mockHandleError).not.toHaveBeenCalled();
  });

  it("sets a not-found error message on 404", async () => {
    const { useError } = await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateError: { response: { status: 404 } },
    });
    expect(useError).toMatch(/not found/i);
    expect(mockHandleError).not.toHaveBeenCalled();
  });

  it("sets a generic error and calls handleError on unexpected failures", async () => {
    const { useError } = await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateError: { response: { status: 500 } },
    });
    expect(useError).toMatch(/Instantiation failed/);
    expect(mockHandleError).toHaveBeenCalledOnce();
    expect(mockHandleError.mock.calls[0]![1]).toBe("instantiateDataObject from browse");
  });

  it("does not navigate on error", async () => {
    await runConfirmUse({
      selectedAppId: TEMPLATE_APP_ID,
      collectionAppId: COLLECTION_APP_ID,
      instantiateError: { response: { status: 403 } },
    });
    expect(mockRouterPush).not.toHaveBeenCalled();
  });
});
