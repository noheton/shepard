/**
 * V2-SWEEP Wave 1 (2026-06-10) — `useTreeviewItems` is v2-only and
 * appId-native.
 *
 * The sidebar tree loads exclusively from the v2 appId-keyed list
 * `GET /v2/collections/{collectionAppId}/data-objects` (via the generated
 * `DataObjectV2Api` through `useV2ShepardApi`) and materialises the whole
 * tree client-side. The v1 helper (`useShepardApi`) and the v1
 * `getAllDataObjects` list are GONE — the numeric-collection-id gate that
 * left the sidebar spinning on appId-only data (operator regression
 * 2026-06-10) is structurally impossible now.
 *
 * Coverage:
 *   - tree builds from the paged v2 list, keyed on appIds
 *   - pagination loop exhausts pages until a short page arrives
 *   - parent/child linkage resolves to appIds; sidebar values are appIds
 *   - ancestors auto-expand for the routed dataObjectId (appId AND legacy
 *     numeric forms)
 *   - v2 list failure flips loadError (no infinite spinner)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import type { DataObjectListItemV2 } from "@dlr-shepard/backend-client";

const listDataObjects = vi.fn();
const addOpen = vi.fn();

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectV2Api: function DataObjectV2Api() {},
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ref({ listDataObjects }),
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

function row(
  id: number,
  appId: string,
  name: string,
  parentId: number | null = null,
  childrenIds: number[] = [],
): DataObjectListItemV2 {
  return { id, appId, name, parentId, childrenIds } as DataObjectListItemV2;
}

const ROWS = [
  row(100, "019e0000-0000-7000-8000-000000000100", "TR-001"),
  row(200, "019e0000-0000-7000-8000-000000000200", "TR-004", null, [300]),
  row(300, "019e0000-0000-7000-8000-000000000300", "Investigation", 200, [400]),
  row(400, "019e0000-0000-7000-8000-000000000400", "Vibration analysis", 300),
];

beforeEach(() => {
  vi.clearAllMocks();
  listDataObjects.mockResolvedValue(ROWS);
});

async function load(routeParams: Record<string, unknown>) {
  const mod = await import("~/components/context/sidebar/useTreeviewItems");
  return mod.useTreeviewItems(
    ref(routeParams) as unknown as Parameters<typeof mod.useTreeviewItems>[0],
  );
}

describe("useTreeviewItems — V2-SWEEP Wave 1", () => {
  it("loads the tree from the v2 appId-keyed list; never touches the v1 helper", async () => {
    const { treeviewItems, loading, loadError } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();

    expect(listDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({
        collectionAppId: COLLECTION_APP_ID,
        page: 0,
      }),
    );
    expect(v1HelperUsed).not.toHaveBeenCalled();
    expect(loading.value).toBe(false);
    expect(loadError.value).toBe(false);

    // Roots keyed on appIds; children attached and appId-linked.
    expect(treeviewItems.value?.map(i => i.id)).toEqual([
      "019e0000-0000-7000-8000-000000000100",
      "019e0000-0000-7000-8000-000000000200",
    ]);
    const tr004 = treeviewItems.value?.[1];
    expect(tr004?.childrenIds).toEqual([
      "019e0000-0000-7000-8000-000000000300",
    ]);
    expect(tr004?.children?.[0]?.parentId).toBe(tr004?.id);
  });

  it("exhausts pages until a short page arrives", async () => {
    const fullPage = Array.from({ length: 200 }, (_, i) =>
      row(
        1000 + i,
        `019e0000-0000-7000-8000-${String(1000 + i).padStart(12, "0")}`,
        `DO-${i}`,
      ),
    );
    listDataObjects
      .mockResolvedValueOnce(fullPage)
      .mockResolvedValueOnce([row(9999, "019e0000-0000-7000-8000-000000009999", "last")]);

    const { treeviewItems } = await load({ collectionId: COLLECTION_APP_ID });
    await flush();

    expect(listDataObjects).toHaveBeenCalledTimes(2);
    expect(listDataObjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
    );
    expect(treeviewItems.value).toHaveLength(201);
  });

  it("auto-expands ancestors of the routed dataObjectId (appId form)", async () => {
    await load({
      collectionId: COLLECTION_APP_ID,
      dataObjectId: "019e0000-0000-7000-8000-000000000400",
    });
    await flush();

    expect(addOpen).toHaveBeenCalledWith([
      "019e0000-0000-7000-8000-000000000200",
      "019e0000-0000-7000-8000-000000000300",
    ]);
  });

  it("auto-expands ancestors for a legacy numeric dataObjectId deep-link", async () => {
    await load({
      collectionId: COLLECTION_APP_ID,
      dataObjectId: "400",
    });
    await flush();

    expect(addOpen).toHaveBeenCalledWith([
      "019e0000-0000-7000-8000-000000000200",
      "019e0000-0000-7000-8000-000000000300",
    ]);
  });

  it("flips loadError true (NOT infinite spinner) when the v2 list rejects", async () => {
    listDataObjects.mockRejectedValueOnce(
      Object.assign(new Error("HTTP 500"), { response: { status: 500 } }),
    );
    const { loadError, loading, treeviewItems } = await load({
      collectionId: COLLECTION_APP_ID,
    });
    await flush();

    expect(loadError.value).toBe(true);
    expect(loading.value).toBe(false);
    // [] (not undefined) — the template uses that to suppress the spinner
    // and show the explicit error state instead.
    expect(treeviewItems.value).toEqual([]);
  });
});
