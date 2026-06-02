/**
 * BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH — unit tests proving that
 * `useUpdateDataObjectRelationship` sends `predecessorAppIds` in the PATCH body
 * when the predecessor DataObject has a UUID v7 appId, and that it routes
 * through the v2 appId-keyed endpoint (BUG-COLL-APPID-ROUTE-003 behaviour
 * retained).
 *
 * Coverage:
 *   - addPredecessor() sends predecessorAppIds when new predecessor id is a UUID v7
 *   - addPredecessor() does NOT send predecessorAppIds when ids are numeric only
 *   - deletePredecessor() rebuilds predecessorAppIds excluding the removed appId
 *   - v2 routing is used for both GET and PATCH (BUG-COLL-APPID-ROUTE-003 check)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

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

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const COLL_APP = "019e6ffc-1111-7abc-9def-000000000001";
const DO_APP = "019e6ffc-2222-7abc-9def-000000000002";
/** A UUID v7 style id string for a post-reset predecessor DataObject. */
const PRED_UUID_V7 = "019e6ffc-3333-7abc-9def-000000000003";

function makeBodyWithNumericPredecessors() {
  return {
    id: 100,
    appId: DO_APP,
    collectionId: 1,
    name: "TR-004",
    predecessorIds: [10, 20],
    successorIds: [],
  };
}

function makeBodyWithUuidPredecessors() {
  // Predecessor ids are UUID v7-looking strings cast to numbers by the caller.
  // The page layer casts route segments to `number` even when they are UUID strings.
  return {
    id: 100,
    appId: DO_APP,
    collectionId: 1,
    name: "TR-004",
    // We store the UUID v7 string in the numeric predecessorIds array using the
    // same page-level cast the real caller performs.
    predecessorIds: [] as number[],
    successorIds: [],
  };
}

describe("useUpdateDataObjectRelationship — BUG-PREDECESSOR-IDS-NUMERIC-IN-V2-PATCH", () => {
  it("addPredecessor sends predecessorAppIds when new predecessor id looks like UUID v7", async () => {
    const fetchSpy = vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      if (!init.method || init.method === "GET") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(makeBodyWithUuidPredecessors()),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ ...makeBodyWithUuidPredecessors() }),
      });
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const onSuccess = vi.fn();
    const { addPredecessor } = useUpdateDataObjectRelationship(COLL_APP, onSuccess);

    // Caller passes the UUID v7 as the "numeric" id (page-level cast).
    await addPredecessor(
      DO_APP as unknown as number,
      PRED_UUID_V7 as unknown as number,
    );
    await flush();

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const patchCall = fetchSpy.mock.calls[1];
    expect(patchCall?.[1].method).toBe("PATCH");

    const body = JSON.parse(patchCall?.[1].body as string);
    // The new UUID v7 predecessor must appear in predecessorAppIds.
    expect(body.predecessorAppIds).toBeDefined();
    expect(body.predecessorAppIds).toContain(PRED_UUID_V7);
  });

  it("addPredecessor does NOT include predecessorAppIds when ids are numeric only", async () => {
    const fetchSpy = vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      if (!init.method || init.method === "GET") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve(makeBodyWithNumericPredecessors()),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ ...makeBodyWithNumericPredecessors(), predecessorIds: [10, 20, 30] }),
      });
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const { addPredecessor } = useUpdateDataObjectRelationship(COLL_APP, () => undefined);
    await addPredecessor(100, 30);
    await flush();

    const patchCall = fetchSpy.mock.calls[1];
    const body = JSON.parse(patchCall?.[1].body as string);
    // No UUID v7-shaped ids in the mix → predecessorAppIds must be absent.
    expect(body.predecessorAppIds).toBeUndefined();
    expect(body.predecessorIds).toEqual([10, 20, 30]);
  });

  it("deletePredecessor rebuilds predecessorAppIds excluding the removed UUID v7 id", async () => {
    // DataObject currently has two UUID v7-shaped predecessors.
    const PRED_A = "019e6ffc-aaaa-7abc-9def-000000000011";
    const PRED_B = "019e6ffc-bbbb-7abc-9def-000000000022";

    const fetchSpy = vi.fn().mockImplementation((_url: string, init: RequestInit) => {
      if (!init.method || init.method === "GET") {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () =>
            Promise.resolve({
              id: 100,
              appId: DO_APP,
              collectionId: 1,
              name: "TR-004",
              // Both are stored as UUID strings in the numeric array slot.
              predecessorIds: [
                PRED_A as unknown as number,
                PRED_B as unknown as number,
              ],
              successorIds: [],
            }),
        });
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ predecessorIds: [PRED_A as unknown as number] }),
      });
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const { deletePredecessor } = useUpdateDataObjectRelationship(COLL_APP, () => undefined);
    // Delete PRED_B.
    await deletePredecessor(
      DO_APP as unknown as number,
      PRED_B as unknown as number,
    );
    await flush();

    const patchCall = fetchSpy.mock.calls[1];
    const body = JSON.parse(patchCall?.[1].body as string);
    // predecessorAppIds must contain PRED_A only (PRED_B excluded).
    expect(body.predecessorAppIds).toBeDefined();
    expect(body.predecessorAppIds).toContain(PRED_A);
    expect(body.predecessorAppIds).not.toContain(PRED_B);
  });

  it("routes GET + PATCH through v2 appId-keyed endpoint (BUG-COLL-APPID-ROUTE-003 retained)", async () => {
    const fetchSpy = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(makeBodyWithNumericPredecessors()),
    });
    vi.stubGlobal("fetch", fetchSpy);

    const { useUpdateDataObjectRelationship } = await import(
      "~/composables/references/useUpdateDataObjectPredecessor"
    );
    const { addPredecessor } = useUpdateDataObjectRelationship(COLL_APP, () => undefined);
    await addPredecessor(100, 30);
    await flush();

    const getUrl = fetchSpy.mock.calls[0]?.[0] as string;
    const patchUrl = fetchSpy.mock.calls[1]?.[0] as string;
    const expectedBase =
      `http://localhost:8080/v2/collections/` +
      `${encodeURIComponent(COLL_APP)}/data-objects/` +
      `${encodeURIComponent("100")}`;
    expect(getUrl).toBe(expectedBase);
    expect(patchUrl).toBe(expectedBase);
    expect(fetchSpy.mock.calls[1]?.[1].method).toBe("PATCH");
  });
});
