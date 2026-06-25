/**
 * SIDEBAR-LAZY-TREE (2026-06-25) — `useTreeviewItems` is v2-only, appId-native,
 * AND lazy-loaded.
 *
 * The sidebar tree loads one hierarchy level at a time from the v2 appId-keyed
 * list `GET /v2/collections/{collectionAppId}/data-objects`:
 *   - initial load:  `?topLevel=true`  → the root DataObjects
 *   - on expand:     `?parentAppId=…`  → that node's direct children
 * Children placeholders (`[]`) drive Vuetify's `load-children`; leaves get
 * `children: undefined` (no chevron). The v1 helper (`useShepardApi`) is GONE.
 *
 * Coverage:
 *   - roots load via ?topLevel=true (NOT a full-tree fetch)
 *   - expandable roots get a `[]` placeholder; leaves get `undefined`
 *   - loadChildren fetches ?parentAppId=<id> and replaces the placeholder
 *   - loadChildren caches (no refetch on re-expand)
 *   - deep-link ancestor walk uses getDataObjectV2 parentSummary then expands
 *   - v2 list failure flips loadError (no infinite spinner)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";

const listDataObjects = vi.fn();
const getDataObjectV2 = vi.fn();
const addOpen = vi.fn();

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectsApi: function DataObjectsApi() {},
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ref({ listDataObjects, getDataObjectV2 }),
}));
// The v1 helper must not even be imported by the module under test; if a
// regression re-introduces it, this mock makes the call observable.
const v1HelperUsed = vi.fn();
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: v1HelperUsed,
}));
vi.mock("~/components/context/sidebar/useOpenedItems", () => ({
  useOpenedItems: () => ({
    openedTreeviewItems: ref<string[]>([]),
    addOpen,
    collapseItem: vi.fn(),
  }),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const COLLECTION_APP_ID = "019e6ffc-aaaa-7bcd-9eef-000000000042";

type Row = DataObjectListItemV2 & { childrenAppIds?: string[] | null };

function row(
  id: number,
  appId: string,
  name: string,
  childrenAppIds: string[] = [],
): Row {
  return { id, appId, name, childrenAppIds } as Row;
}

const ROOT_LEAF = "019e0000-0000-7000-8000-000000000100";
const ROOT_PARENT = "019e0000-0000-7000-8000-000000000200";
const CHILD = "019e0000-0000-7000-8000-000000000300";
const GRANDCHILD = "019e0000-0000-7000-8000-000000000400";

const ROOT_ROWS = [
  row(100, ROOT_LEAF, "TR-001"),
  row(200, ROOT_PARENT, "TR-004", [CHILD]),
];

beforeEach(() => {
  vi.clearAllMocks();
  // Default: any list call returns the roots. Tests override per-call.
  listDataObjects.mockResolvedValue(ROOT_ROWS);
});

async function load(routeParams: Record<string, unknown>) {
  const mod = await import("~/components/context/sidebar/useTreeviewItems");
  return mod.useTreeviewItems(
    ref(routeParams) as unknown as Parameters<typeof mod.useTreeviewItems>[0],
  );
}

describe("useTreeviewItems — SIDEBAR-LAZY-TREE", () => {
  it("loads roots via ?topLevel=true (never the v1 helper)", async () => {
    const { treeviewItems, loading, loadError } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();

    expect(listDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({
        collectionAppId: COLLECTION_APP_ID,
        topLevel: true,
        page: 0,
      }),
    );
    expect(v1HelperUsed).not.toHaveBeenCalled();
    expect(loading.value).toBe(false);
    expect(loadError.value).toBe(false);
    expect(treeviewItems.value?.map(i => i.id)).toEqual([ROOT_LEAF, ROOT_PARENT]);
  });

  it("gives expandable roots a [] placeholder and leaves undefined children", async () => {
    const { treeviewItems } = await load({ collectionId: COLLECTION_APP_ID });
    await flush();

    const leaf = treeviewItems.value?.find(i => i.id === ROOT_LEAF);
    const parent = treeviewItems.value?.find(i => i.id === ROOT_PARENT);
    // Leaf: no chevron → children undefined.
    expect(leaf?.children).toBeUndefined();
    // Expandable: empty-array placeholder (Vuetify lazy trigger).
    expect(parent?.children).toEqual([]);
    expect(parent?.childrenIds).toEqual([CHILD]);
  });

  it("loadChildren fetches ?parentAppId=<id> and replaces the placeholder", async () => {
    const { treeviewItems, loadChildren } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();

    const parent = treeviewItems.value!.find(i => i.id === ROOT_PARENT)!;
    listDataObjects.mockResolvedValueOnce([
      row(300, CHILD, "Investigation", [GRANDCHILD]),
    ]);

    await loadChildren(parent);

    expect(listDataObjects).toHaveBeenLastCalledWith(
      expect.objectContaining({
        collectionAppId: COLLECTION_APP_ID,
        parentAppId: ROOT_PARENT,
      }),
    );
    expect(parent.children?.map(c => c.id)).toEqual([CHILD]);
    // The fetched child is itself expandable (has a grandchild).
    expect(parent.children?.[0]?.children).toEqual([]);
    expect(parent.children?.[0]?.parentId).toBe(ROOT_PARENT);
  });

  it("caches loaded children (no refetch on re-expand)", async () => {
    const { treeviewItems, loadChildren } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();
    const parent = treeviewItems.value!.find(i => i.id === ROOT_PARENT)!;
    listDataObjects.mockResolvedValueOnce([row(300, CHILD, "Investigation")]);

    await loadChildren(parent);
    const callsAfterFirst = listDataObjects.mock.calls.length;
    await loadChildren(parent); // second expand — must NOT refetch

    expect(listDataObjects.mock.calls.length).toBe(callsAfterFirst);
  });

  it("deep-link: walks parentSummary then expands the ancestor chain", async () => {
    // Routed dataObjectId is the grandchild, not present in the loaded roots.
    // getDataObjectV2 walk: grandchild → child → parent → (root, no parent).
    getDataObjectV2
      .mockResolvedValueOnce({ parentSummary: { appId: CHILD } })
      .mockResolvedValueOnce({ parentSummary: { appId: ROOT_PARENT } })
      .mockResolvedValueOnce({ parentSummary: null });

    // Roots first; then lazy-loads for ROOT_PARENT (→ child) and CHILD (→ gc).
    listDataObjects
      .mockResolvedValueOnce(ROOT_ROWS) // initial roots
      .mockResolvedValueOnce([row(300, CHILD, "Investigation", [GRANDCHILD])]) // children of ROOT_PARENT
      .mockResolvedValueOnce([row(400, GRANDCHILD, "Vibration")]); // children of CHILD

    await load({
      collectionId: COLLECTION_APP_ID,
      dataObjectId: GRANDCHILD,
    });
    await flush();

    // Ancestors opened root→parent: [ROOT_PARENT, CHILD].
    expect(addOpen).toHaveBeenCalledWith([ROOT_PARENT, CHILD]);
  });

  it("flips loadError true (NOT infinite spinner) when the roots fetch rejects", async () => {
    listDataObjects.mockReset();
    listDataObjects.mockRejectedValueOnce(
      Object.assign(new Error("HTTP 500"), { response: { status: 500 } }),
    );
    const { loadError, loading, treeviewItems } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();

    expect(loadError.value).toBe(true);
    expect(loading.value).toBe(false);
    expect(treeviewItems.value).toEqual([]);
  });
});
