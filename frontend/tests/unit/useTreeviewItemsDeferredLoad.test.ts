/**
 * BUG-COLL-APPID-ROUTE-006 — useTreeviewItems deferred v1 list-fetch.
 *
 * Pre-006 the sidebar treeview called v1 `getAllDataObjects({ collectionId,
 * parentId: -1 })` with the raw route param cast to number. When that param
 * carried a UUID v7 string post-Neo4j-reset, the request 400'd at the JAX-RS
 * binding (the v1 path param is a primitive `Long`) and the treeview spun
 * forever — the operator-surfaced LUMEN regression on 2026-06-03.
 *
 * Fix: the composable accepts an explicit `collectionNumericId` from the
 * caller (the page resolves it from the loaded v2 Collection's `.id`) and
 * gates the v1 list call on the ref being defined. When the call errors,
 * `loadError` flips to true and the template renders an explicit error
 * state instead of a permanent spinner.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useTreeviewItems } from "~/components/context/sidebar/useTreeviewItems";

const getAllDataObjects = vi.fn();

vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ref({ getAllDataObjects }),
}));

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: {},
  ResponseError: class ResponseError extends Error {
    constructor(public response: Response, msg?: string) { super(msg); }
  },
}));

// useOpenedItems uses useState under the hood; the setup.ts shim returns
// real refs, so a bare import works here.
const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
  getAllDataObjects.mockResolvedValue([]);
});

describe("useTreeviewItems — deferred numeric-id fetch", () => {
  it("does NOT call v1 getAllDataObjects while collectionNumericId is undefined", async () => {
    const routeParams = ref({
      // UUID v7 route param — the post-reset shape that 400'd pre-006.
      collectionId: "019e6ffc-89a4-76b5-8dbb-15888646a904",
    });
    const numericId = ref<number | undefined>(undefined);
    useTreeviewItems(
      routeParams as unknown as Parameters<typeof useTreeviewItems>[0],
      numericId,
    );
    await flush();
    expect(getAllDataObjects).not.toHaveBeenCalled();
  });

  it("calls v1 getAllDataObjects with the resolved numeric id once it materialises", async () => {
    const routeParams = ref({
      collectionId: "019e6ffc-89a4-76b5-8dbb-15888646a904",
    });
    const numericId = ref<number | undefined>(undefined);
    useTreeviewItems(
      routeParams as unknown as Parameters<typeof useTreeviewItems>[0],
      numericId,
    );
    await flush();
    expect(getAllDataObjects).not.toHaveBeenCalled();

    // v2 Collection fetch resolves → numeric id arrives.
    numericId.value = 2107;
    await flush();
    expect(getAllDataObjects).toHaveBeenCalledWith({
      collectionId: 2107,
      parentId: -1,
    });
  });

  it("falls back to a legacy numeric route param when no explicit id ref is provided", async () => {
    const routeParams = ref({
      // Pre-L2d legacy /collections/123 deep link.
      collectionId: "2107",
    });
    useTreeviewItems(
      routeParams as unknown as Parameters<typeof useTreeviewItems>[0],
    );
    await flush();
    expect(getAllDataObjects).toHaveBeenCalledWith({
      collectionId: 2107,
      parentId: -1,
    });
  });

  it("flips loadError true (NOT infinite spinner) when the v1 list call rejects", async () => {
    const handleErrorSpy = vi.fn();
    vi.stubGlobal("handleError", handleErrorSpy);

    getAllDataObjects.mockRejectedValueOnce(
      Object.assign(new Error("HTTP 400"), {
        response: { status: 400 },
      }),
    );
    const routeParams = ref({ collectionId: "2107" });
    const { loadError, loading, treeviewItems } = useTreeviewItems(
      routeParams as unknown as Parameters<typeof useTreeviewItems>[0],
    );
    await flush();
    expect(loadError.value).toBe(true);
    expect(loading.value).toBe(false);
    // treeviewItems is now [] (not undefined) — the template uses that to
    // suppress the loading spinner and show the error state instead.
    expect(treeviewItems.value).toEqual([]);
  });
});
