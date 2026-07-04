/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves `useEditDataObject`
 * PATCHes through the v2 appId-keyed endpoint with the merge-patch
 * Content-Type. Pre-fix the v1 generated `updateDataObject` expected
 * numeric Neo4j longs; post-Neo4j-reset DataObjects carry UUID v7 only
 * so the edit-dialog Save silently 404'd.
 *
 * Coverage:
 *   - PATCH /v2/collections/{cId}/data-objects/{dId} with merge-patch+json
 *   - v1 generated client `updateDataObject` is NOT called
 *   - placeholder -1 predecessor ids are stripped before sending
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1UpdateDataObject = vi.fn();
const dataObjectUpdatedListeners: Array<() => void> = [];

beforeEach(() => {
  vi.clearAllMocks();
  dataObjectUpdatedListeners.length = 0;
  Object.assign(globalThis, {
    useAuth: () => ({
      data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
    }),
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    handleError: vi.fn(),
    emitSuccess: vi.fn(),
    handleDataObjectUpdate: vi.fn(),
    uniqueNumbersOf: (arr: number[]) => Array.from(new Set(arr)),
    onDataObjectUpdated: (cb: () => void) => dataObjectUpdatedListeners.push(cb),
    useFetchDataObject: (_cAppId: string, _dAppId: string) => {
      // Returned dataObject is initially undefined (matching real behaviour);
      // the test populates it on next tick so the watcher inside
      // useEditDataObject fires and hydrates updatedDataObject.
      const dataObject = ref<{ id: number; name: string; description: string; attributes: Record<string, string>; predecessorIds: number[]; status: string | null; parentId: number | null } | undefined>(undefined);
      // Schedule the hydration on a microtask so the watcher in
      // useEditDataObject has already registered.
      Promise.resolve().then(() => {
        dataObject.value = {
          id: 4242,
          name: "TR-004",
          description: "anomaly",
          attributes: { campaign: "Q3" },
          predecessorIds: [],
          status: null,
          parentId: null,
        };
      });
      return { dataObject, isLoading: ref(false), notFound: ref(false) };
    },
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: function DataObjectApi() {},
  ResponseError: class ResponseError extends Error {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ref({ updateDataObject: v1UpdateDataObject }),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useEditDataObject — BUG-COLL-APPID-ROUTE-005", () => {
  it("PATCHes via /v2/collections/{cId}/data-objects/{dId} with merge-patch+json", async () => {
    // Mock the wrapped useFetchDataObject to short-circuit the GET.
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((url: string, init?: RequestInit) => {
        if (init?.method === "PATCH") {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve({}),
          });
        }
        // initial GET from useFetchDataObject
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({
              id: 4242,
              appId: "019e6ffc-aaaa-7bcd-9eef-000000000042",
              name: "TR-004",
              description: "anomaly",
              attributes: { campaign: "Q3" },
              predecessorIds: [],
              parentId: null,
            }),
        });
      }),
    );

    const mod = await import(
      "~/components/context/data-object/edit-dialog/useEditDataObject"
    );
    const isValid = ref(true);
    const onSuccess = vi.fn();
    const { updatedDataObject, saveChanges } = mod.useEditDataObject(
      42,
      4242,
      isValid,
      onSuccess,
    );
    await flush();
    await flush();

    // Wait for the GET-then-watch chain to populate updatedDataObject.
    expect(updatedDataObject.value).toBeDefined();
    updatedDataObject.value!.name = "TR-004-renamed";
    updatedDataObject.value!.predecessorIds = [123, -1, 456];

    await saveChanges();
    await flush();

    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    const patchCall = calls.find(
      c => (c[1] as RequestInit | undefined)?.method === "PATCH",
    );
    expect(patchCall).toBeTruthy();
    expect(patchCall?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}/data-objects/${encodeURIComponent("4242")}`,
    );
    const init = patchCall?.[1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/merge-patch+json");
    const body = JSON.parse(init.body as string) as { name: string; predecessorIds: number[] };
    expect(body.name).toBe("TR-004-renamed");
    // -1 placeholder stripped, duplicates removed by uniqueNumbersOf stub.
    expect(body.predecessorIds).toEqual([123, 456]);
    expect(v1UpdateDataObject).not.toHaveBeenCalled();
    expect(onSuccess).toHaveBeenCalled();
  });
});
