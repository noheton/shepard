/**
 * BUG-COLL-APPID-ROUTE-002 — proves useFetchDataObject hits the v2 appId-keyed
 * single-DO endpoint:
 *   GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 *
 * Coverage:
 *   - URL built from appId strings via encodeURIComponent
 *   - Bearer header carries the session accessToken
 *   - response shape (DataObjectDetailV2IO ⊇ DataObjectIO) survives the
 *     `description ?? ""` sanitisation
 *   - HTTP error leaves dataObject undefined and calls handleError
 *   - missing accessToken doesn't fetch and doesn't throw
 *   - re-fetches when onDataObjectUpdated fires
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

const dataObjectUpdatedListeners: Array<() => void> = [];

beforeEach(() => {
  vi.clearAllMocks();
  dataObjectUpdatedListeners.length = 0;
  Object.assign(globalThis, {
    useAuth: () => ({ data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }) }),
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    handleError: vi.fn(),
    onDataObjectUpdated: (cb: () => void) => dataObjectUpdatedListeners.push(cb),
  });
  vi.stubGlobal("fetch", vi.fn());
});

const flush = () => new Promise<void>(r => setTimeout(r, 0));

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchStatus(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(""),
    }),
  );
}

const COLL_APP_ID = "019e6ffc-1234-7abc-9def-000000000042";
const DO_APP_ID = "019e6ffc-1234-7abc-9def-000000000100";

const dataObjectBody = {
  id: 100,
  appId: DO_APP_ID,
  collectionId: 42,
  name: "TR-004",
  description: "anomaly test run",
  status: "READY",
  attributes: { propellant: "LOX/LH2" },
  referenceIds: [1, 2, 3],
  successorIds: [],
  childrenIds: [],
  parentId: null,
  incomingIds: [],
};

describe("useFetchDataObject — BUG-COLL-APPID-ROUTE-002", () => {
  it("calls GET /v2/collections/{appId}/data-objects/{appId}", async () => {
    mockFetchOk(dataObjectBody);
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(COLL_APP_ID)}/data-objects/${encodeURIComponent(DO_APP_ID)}`,
    );
    const calledInit = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[1];
    expect(calledInit.headers.Authorization).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("sanitises null description to empty string", async () => {
    mockFetchOk({ ...dataObjectBody, description: null });
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    const { dataObject } = useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();
    await flush();
    expect(dataObject.value?.description).toBe("");
  });

  it("populates dataObject.value with the response shape", async () => {
    mockFetchOk(dataObjectBody);
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    const { dataObject, isLoading } = useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();
    await flush();
    expect(dataObject.value?.name).toBe("TR-004");
    expect(dataObject.value?.attributes?.propellant).toBe("LOX/LH2");
    expect(isLoading.value).toBe(false);
  });

  // UU1 — UI-404-NICE-EMPTY-STATE (2026-05-31): on 404 the composable sets
  // `notFound=true` and SUPPRESSES `handleError` so the page renders an
  // `EntityNotFound` instead of the red "Error while getDataObject:" toast.
  // Non-404 errors still call `handleError` and leave `notFound=false`.
  it("on HTTP 404 sets notFound and does NOT call handleError", async () => {
    mockFetchStatus(404);
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    const { dataObject, notFound, isLoading } = useFetchDataObject(
      COLL_APP_ID,
      DO_APP_ID,
    );
    await flush();
    expect(dataObject.value).toBeUndefined();
    expect(notFound.value).toBe(true);
    expect(isLoading.value).toBe(false);
    expect(
      (globalThis as unknown as { handleError: ReturnType<typeof vi.fn> })
        .handleError,
    ).not.toHaveBeenCalled();
  });

  it("on HTTP 500 calls handleError and leaves notFound=false", async () => {
    mockFetchStatus(500);
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    const { dataObject, notFound } = useFetchDataObject(
      COLL_APP_ID,
      DO_APP_ID,
    );
    await flush();
    expect(dataObject.value).toBeUndefined();
    expect(notFound.value).toBe(false);
    expect(
      (globalThis as unknown as { handleError: ReturnType<typeof vi.fn> })
        .handleError,
    ).toHaveBeenCalledTimes(1);
  });

  it("does not fetch when unauthenticated", async () => {
    mockFetchOk(dataObjectBody);
    Object.assign(globalThis, {
      useAuth: () => ({ data: ref(null) }),
    });
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("re-fetches when onDataObjectUpdated fires", async () => {
    mockFetchOk(dataObjectBody);
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();
    await flush();
    expect(fetch).toHaveBeenCalledTimes(1);
    dataObjectUpdatedListeners.forEach(l => l());
    await flush();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("strips /shepard/api/ suffix when computing the v2 base", async () => {
    mockFetchOk(dataObjectBody);
    Object.assign(globalThis, {
      useRuntimeConfig: () => ({
        public: { backendApiUrl: "https://shepard.nuclide.systems/shepard/api/" },
      }),
    });
    const { useFetchDataObject } = await import(
      "~/composables/context/useFetchDataObject"
    );
    useFetchDataObject(COLL_APP_ID, DO_APP_ID);
    await flush();
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl?.startsWith("https://shepard.nuclide.systems/v2/")).toBe(
      true,
    );
  });
});
