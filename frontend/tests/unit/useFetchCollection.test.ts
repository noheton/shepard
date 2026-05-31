/**
 * BUG-COLL-APPID-ROUTE-002 — proves useFetchCollection hits the v2 appId-keyed
 * endpoint with the route param as-is (no `parseInt` truncation, no v1 path).
 * Coverage:
 *   - calls GET /v2/collections/{appId} on construction
 *   - parses the JSON response into `collection.value`
 *   - looks up Roles via the v1 client using the numeric `id` field
 *   - handles HTTP errors (404 / 500) cleanly, isError=true
 *   - handles missing accessToken (unauthenticated) without throwing
 *   - useFetchCollectionOfRouteParams re-fetches when the route param flips
 *     between an appId and a numeric form
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

// Auto-imports the composable consumes — set up before importing the module.
const mockGetCollectionRoles = vi.fn();
const mockUseShepardApi = vi.fn(() => ref({ getCollectionRoles: mockGetCollectionRoles }));
const collectionUpdatedListeners: Array<() => void> = [];

beforeEach(() => {
  vi.clearAllMocks();
  collectionUpdatedListeners.length = 0;
  mockGetCollectionRoles.mockResolvedValue({
    owner: true,
    writer: true,
    reader: true,
    manager: false,
  });
  Object.assign(globalThis, {
    useAuth: () => ({ data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }) }),
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    handleError: vi.fn(),
    onCollectionUpdated: (cb: () => void) => collectionUpdatedListeners.push(cb),
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => mockUseShepardApi(),
}));

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

const APP_ID = "019e6ffc-1234-7abc-9def-000000000042";

const collectionBody = {
  id: 42,
  appId: APP_ID,
  name: "LUMEN Showcase",
  description: "synthetic",
  status: "READY",
  attributes: {},
  dataObjectIds: [],
  incomingIds: [],
};

describe("useFetchCollection — BUG-COLL-APPID-ROUTE-002", () => {
  it("calls the v2 appId-keyed endpoint, not v1", async () => {
    mockFetchOk(collectionBody);
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    useFetchCollection(APP_ID);
    await flush();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(APP_ID)}`,
    );
    // Bearer header present.
    const calledInit = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[1];
    expect(calledInit.headers.Authorization).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("strips /shepard/api/ suffix when deriving the v2 base", async () => {
    mockFetchOk(collectionBody);
    Object.assign(globalThis, {
      useRuntimeConfig: () => ({
        public: { backendApiUrl: "https://shepard.nuclide.systems/shepard/api" },
      }),
    });
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    useFetchCollection(APP_ID);
    await flush();

    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl).toBe(
      `https://shepard.nuclide.systems/v2/collections/${encodeURIComponent(APP_ID)}`,
    );
  });

  it("populates collection.value from the JSON response", async () => {
    mockFetchOk(collectionBody);
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    const { collection, isError } = useFetchCollection(APP_ID);
    await flush();
    await flush();

    expect(collection.value?.name).toBe("LUMEN Showcase");
    expect(isError.value).toBe(false);
  });

  it("looks up Roles via v1 client with the numeric id from the v2 payload", async () => {
    mockFetchOk(collectionBody);
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    const { isAllowedToEditCollection } = useFetchCollection(APP_ID);
    await flush();
    await flush();

    expect(mockGetCollectionRoles).toHaveBeenCalledWith({ collectionId: 42 });
    expect(isAllowedToEditCollection.value).toBe(true);
  });

  it("sets isError=true on HTTP 404 and clears collection", async () => {
    mockFetchStatus(404);
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    const { collection, isError, isLoading } = useFetchCollection(APP_ID);
    await flush();

    expect(collection.value).toBeUndefined();
    expect(isError.value).toBe(true);
    expect(isLoading.value).toBe(false);
  });

  it("handles missing access token without throwing", async () => {
    mockFetchOk(collectionBody);
    Object.assign(globalThis, {
      useAuth: () => ({ data: ref(null) }),
    });
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    const { isError } = useFetchCollection(APP_ID);
    await flush();
    expect(fetch).not.toHaveBeenCalled();
    expect(isError.value).toBe(true);
  });

  it("re-fetches when onCollectionUpdated fires", async () => {
    mockFetchOk(collectionBody);
    const { useFetchCollection } = await import(
      "~/composables/context/useFetchCollection"
    );
    useFetchCollection(APP_ID);
    await flush();
    await flush();
    expect(fetch).toHaveBeenCalledTimes(1);

    collectionUpdatedListeners.forEach(l => l());
    await flush();
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});

describe("useFetchCollectionOfRouteParams — BUG-COLL-APPID-ROUTE-002", () => {
  it("accepts a string route param (UUID v7) and refetches when it flips", async () => {
    // One persistent spy that returns the right body per call, so re-stubbing
    // doesn't reset the call count between the initial and post-flip fetches.
    const OTHER = "019e6ffc-1234-7abc-9def-000000000099";
    const fetchSpy = vi.fn().mockImplementation((url: string) =>
      Promise.resolve({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve(
            url.includes(OTHER)
              ? { ...collectionBody, id: 99, appId: OTHER }
              : collectionBody,
          ),
      }),
    );
    vi.stubGlobal("fetch", fetchSpy);
    const { useFetchCollectionOfRouteParams } = await import(
      "~/composables/context/useFetchCollection"
    );
    const params = ref({ collectionId: APP_ID });
    useFetchCollectionOfRouteParams(params);
    await flush();
    await flush();
    expect(fetchSpy).toHaveBeenCalledTimes(1);

    params.value = { collectionId: OTHER };
    await flush();
    await flush();
    expect(fetchSpy).toHaveBeenCalledTimes(2);
    const calledUrl = fetchSpy.mock.calls[1]?.[0] as string;
    expect(calledUrl).toContain(OTHER);
  });

  it("does not loop when the route carries the stringified legacy numeric id", async () => {
    mockFetchOk(collectionBody);
    const { useFetchCollectionOfRouteParams } = await import(
      "~/composables/context/useFetchCollection"
    );
    const params = ref({ collectionId: "42" });
    useFetchCollectionOfRouteParams(params);
    await flush();
    await flush();
    expect(fetch).toHaveBeenCalledTimes(1);

    // Trigger the watch with the same value (collection has id=42).
    params.value = { collectionId: "42" };
    await flush();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
