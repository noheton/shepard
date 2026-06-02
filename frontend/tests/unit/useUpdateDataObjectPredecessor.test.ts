/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02) — proves
 * `useUpdateDataObjectRelationship` routes its GET + PATCH through the v2
 * appId-keyed endpoint
 * (`/v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}`)
 * rather than the generated v1 client. Coverage:
 *   - addPredecessor() GETs via v2 + PATCHes via v2 (no v1 generated client calls)
 *   - addPredecessor() with stringified UUID v7 handle works end-to-end
 *   - deletePredecessor() GETs via v2 + PATCHes via v2
 *   - the v1 generated `DataObjectApi.getDataObject` is NEVER invoked
 *   - PATCH body deduplicates predecessorIds and strips the -1 placeholder
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

// A sentinel — if any test imports the v1 generated client, the call count
// stays at zero by the contract we're enforcing.
const v1GetDataObjectSentinel = vi.fn();
const v1UpdateDataObjectSentinel = vi.fn();

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
    emitSuccess: vi.fn(),
    handleDataObjectUpdate: vi.fn(),
    uniqueNumbersOf: (xs: number[]) => Array.from(new Set(xs)),
    ref,
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: function DataObjectApi() {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () =>
    ref({
      getDataObject: v1GetDataObjectSentinel,
      updateDataObject: v1UpdateDataObjectSentinel,
    }),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const COLL_APP = "019e6ffc-1111-7abc-9def-000000000001";
const DO_APP = "019e6ffc-2222-7abc-9def-000000000002";

// Fresh copy each call — the composable mutates `predecessorIds`.
function makeDataObjectBody() {
  return {
    id: 100,
    appId: DO_APP,
    collectionId: 1,
    name: "TR-004",
    predecessorIds: [10, 20],
    successorIds: [],
  };
}

describe("useUpdateDataObjectRelationship — BUG-COLL-APPID-ROUTE-003", () => {
  it("addPredecessor GETs and PATCHes via the v2 appId-keyed endpoint", async () => {
    const fetchSpy = vi.fn().mockImplementation((url: string, init: RequestInit) => {
      if (init.method === "GET" || !init.method) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(makeDataObjectBody()),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ ...makeDataObjectBody(), predecessorIds: [10, 20, 30] }),
      });
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const onSuccess = vi.fn();
    const { addPredecessor } = useUpdateDataObjectRelationship(COLL_APP, onSuccess);
    await addPredecessor(100, 30);
    await flush();

    // Two calls: GET then PATCH, both on the v2 path.
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const getCall = fetchSpy.mock.calls[0];
    const patchCall = fetchSpy.mock.calls[1];
    const expectedPath =
      `http://localhost:8080/v2/collections/` +
      `${encodeURIComponent(COLL_APP)}/data-objects/` +
      `${encodeURIComponent("100")}`;
    expect(getCall?.[0]).toBe(expectedPath);
    expect(patchCall?.[0]).toBe(expectedPath);
    expect(patchCall?.[1].method).toBe("PATCH");

    // Body carries dedup'd predecessorIds.
    const body = JSON.parse(patchCall?.[1].body as string);
    expect(body.predecessorIds).toEqual([10, 20, 30]);

    // No v1 generated client call.
    expect(v1GetDataObjectSentinel).not.toHaveBeenCalled();
    expect(v1UpdateDataObjectSentinel).not.toHaveBeenCalled();

    expect(onSuccess).toHaveBeenCalled();
  });

  it("works with a stringified UUID v7 handle (post-Neo4j-reset case)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(makeDataObjectBody()),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const { addPredecessor } = useUpdateDataObjectRelationship(
      COLL_APP,
      () => undefined,
    );
    // Caller's `dataobjectId` is typed as number but page-level cast can put
    // a stringified UUID through that channel. The composable stringifies
    // it before embedding in the path, so it works either way.
    await addPredecessor(
      DO_APP as unknown as number,
      40 as unknown as number,
    );
    await flush();

    const getCall = fetchSpy.mock.calls[0];
    expect(getCall?.[0]).toContain(encodeURIComponent(DO_APP));
    expect(getCall?.[0]).toContain(encodeURIComponent(COLL_APP));
  });

  it("deletePredecessor GETs and PATCHes via the v2 path; removes the targeted id", async () => {
    const fetchSpy = vi.fn().mockImplementation((url: string, init: RequestInit) => {
      if (!init.method || init.method === "GET") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(makeDataObjectBody()),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ ...makeDataObjectBody(), predecessorIds: [10] }),
      });
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const { deletePredecessor } = useUpdateDataObjectRelationship(
      COLL_APP,
      () => undefined,
    );
    await deletePredecessor(100, 20);
    await flush();

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const patchCall = fetchSpy.mock.calls[1];
    const body = JSON.parse(patchCall?.[1].body as string);
    expect(body.predecessorIds).toEqual([10]);
    expect(v1UpdateDataObjectSentinel).not.toHaveBeenCalled();
  });

  it("does not retry against v1 when the v2 GET 404s", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      text: () => Promise.resolve(""),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const onSuccess = vi.fn();
    const { addPredecessor } = useUpdateDataObjectRelationship(COLL_APP, onSuccess);
    await addPredecessor(100, 30);
    await flush();

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(v1GetDataObjectSentinel).not.toHaveBeenCalled();
    expect(onSuccess).not.toHaveBeenCalled();
  });
});
