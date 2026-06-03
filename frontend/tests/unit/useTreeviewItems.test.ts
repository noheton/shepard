/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves `useTreeviewItems` routes
 * single-DataObject lookups through the v2 appId-keyed endpoint instead of
 * the generated v1 client. Pre-fix this composable hit
 * `getDataObject({collectionId, dataObjectId})` expecting numeric Neo4j
 * longs — post-Neo4j-reset DataObjects carry UUID v7 only, so the sidebar
 * tree silently broke when a deep-link landed mid-tree.
 *
 * Coverage:
 *   - fetchTreeviewItem GETs via the v2 path
 *   - v1 generated client `getDataObject` is NOT called
 *   - 404 from v2 doesn't fall back to v1
 *
 * Scope: only `fetchTreeviewItem` (single-item lookup) is migrated. The
 * `fetchTreeviewItems` / `fetchChildrenOfItem` list calls stay on v1
 * `getAllDataObjects` — out of scope for BUG-COLL-APPID-ROUTE-005.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1GetDataObject = vi.fn();
const v1GetAllDataObjects = vi.fn().mockResolvedValue([]);

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, {
    useAuth: () => ({
      data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
    }),
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    handleError: vi.fn(),
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: function DataObjectApi() {},
  ResponseError: class ResponseError extends Error {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () =>
    ref({
      getDataObject: v1GetDataObject,
      getAllDataObjects: v1GetAllDataObjects,
    }),
}));
// Module-path mocks — paths resolve relative to the test's tsconfig `~` alias
// pointing at /opt/shepard/frontend/, NOT a worktree-specific absolute path
// (the pre-006 form used a stale worktree path that silently no-op'd once
// the source moved off main, leaking real implementations into the test).
vi.mock(
  "~/components/context/sidebar/useOpenedItems",
  () => ({
    useOpenedItems: () => ({
      openedTreeviewItems: ref<number[]>([]),
      addOpen: vi.fn(),
      collapseItem: vi.fn(),
    }),
  }),
);
vi.mock(
  "~/components/context/sidebar/treeviewItem",
  () => ({
    mapToTreeviewItem: (item: { id: number; name: string; parentId?: number }) => ({
      id: item.id,
      name: item.name,
      parentId: item.parentId,
      childrenIds: [],
    }),
  }),
);

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useTreeviewItems — BUG-COLL-APPID-ROUTE-005", () => {
  it("routes the single-DataObject lookup through the v2 appId path, not v1 getDataObject", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string, init?: RequestInit) => {
        calls.push({ url, init });
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({
              id: 4242,
              appId: "019e6ffc-1234-7abc-9def-000000000042",
              name: "TR-004",
              parentId: undefined,
              childrenIds: [],
            }),
        });
      }),
    );

    // Bypass the IIFE-style auto-load by importing the module and invoking
    // the inner helper directly. The exported `useTreeviewItems` runs an
    // `initialLoad` on mount, which triggers `fetchTreeviewItems` (a list
    // call — v1, by design). To assert the v2 single-item routing without
    // that noise, we reach for the module's `fetchTreeviewItem` via a
    // forced `getPathToItem` invocation.
    const mod = await import(
      "~/components/context/sidebar/useTreeviewItems"
    );
    const params = ref({
      collectionId: "019e6ffc-aaaa-7bcd-9eef-000000000042" as unknown as number,
      dataObjectId: 4242 as unknown as number | undefined,
    });
    // BUG-COLL-APPID-ROUTE-006 (2026-06-03): the composable now takes a
    // resolved numeric collection id and gates v1 list calls on it being
    // defined. We supply it here so the initialLoad path runs through to
    // `fetchTreeviewItem` (the single-item v2 lookup under test).
    mod.useTreeviewItems(
      params as unknown as Parameters<typeof mod.useTreeviewItems>[0],
      ref(2107),
    );
    await flush();
    await flush();

    // The v2 endpoint should have been hit at least once for the
    // single-item fetchTreeviewItem; the v1 generated `getDataObject`
    // sentinel must NEVER have fired.
    const v2Hits = calls.filter(c =>
      c.url.includes("/v2/collections/") && c.url.includes("/data-objects/"),
    );
    expect(v2Hits.length).toBeGreaterThanOrEqual(1);
    expect(v2Hits[0]?.url).toContain(
      `/v2/collections/${encodeURIComponent(String(params.value.collectionId))}/data-objects/${encodeURIComponent(String(params.value.dataObjectId))}`,
    );
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });

  it("does not fall back to v1 getDataObject when v2 returns 404", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({}),
      }),
    );

    const mod = await import(
      "~/components/context/sidebar/useTreeviewItems"
    );
    const params = ref({
      collectionId: "019e6ffc-aaaa-7bcd-9eef-000000000042" as unknown as number,
      dataObjectId: 4242 as unknown as number | undefined,
    });
    mod.useTreeviewItems(
      params as unknown as Parameters<typeof mod.useTreeviewItems>[0],
      ref(2107),
    );
    await flush();
    await flush();

    expect(v1GetDataObject).not.toHaveBeenCalled();
  });
});
