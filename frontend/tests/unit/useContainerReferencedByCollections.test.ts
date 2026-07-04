/**
 * CC1e — unit tests for useContainerReferencedByCollections composable.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useContainerReferencedByCollections } from "~/composables/containers/useContainerReferencedByCollections";

const ACCESS_TOKEN = "test-token-cc1e";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

/** Flush microtask queue so the auto-triggered async IIFE completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
    }),
  );
}

describe("useContainerReferencedByCollections — unsupported types", () => {
  it("returns null collectionIds and isLoading=false immediately for BASIC", () => {
    const { collectionIds, isLoading } = useContainerReferencedByCollections(1, "BASIC");
    expect(collectionIds.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("returns null collectionIds and isLoading=false immediately for SPATIALDATA", () => {
    const { collectionIds, isLoading } = useContainerReferencedByCollections(2, "SPATIALDATA");
    expect(collectionIds.value).toBeNull();
    expect(isLoading.value).toBe(false);
  });

  it("does not call fetch for unsupported types", () => {
    vi.stubGlobal("fetch", vi.fn());
    useContainerReferencedByCollections(3, "BASIC");
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});

describe("useContainerReferencedByCollections — FILE container", () => {
  it("starts with isLoading=true for FILE", () => {
    mockFetchOk([]);
    const { isLoading } = useContainerReferencedByCollections(10, "FILE");
    expect(isLoading.value).toBe(true);
  });

  it("resolves to empty array when no DataObjects link to it", async () => {
    mockFetchOk([]);
    const { collectionIds, isLoading } = useContainerReferencedByCollections(10, "FILE");
    await flush();
    expect(isLoading.value).toBe(false);
    expect(collectionIds.value).toEqual([]);
  });

  it("resolves unique collection IDs from linked DataObjects", async () => {
    const dataObjects = [
      { id: 1, collectionId: 100, referenceIds: [], successorIds: [], childrenIds: [], parentId: null, incomingIds: [], name: "A", createdAt: "2024-01-01", createdBy: "u1", updatedAt: null, updatedBy: null },
      { id: 2, collectionId: 100, referenceIds: [], successorIds: [], childrenIds: [], parentId: null, incomingIds: [], name: "B", createdAt: "2024-01-01", createdBy: "u1", updatedAt: null, updatedBy: null },
      { id: 3, collectionId: 200, referenceIds: [], successorIds: [], childrenIds: [], parentId: null, incomingIds: [], name: "C", createdAt: "2024-01-01", createdBy: "u1", updatedAt: null, updatedBy: null },
    ];
    mockFetchOk(dataObjects);
    const { collectionIds } = useContainerReferencedByCollections(10, "FILE");
    await flush();
    // Unique collectionIds: [100, 200]
    expect(collectionIds.value).toHaveLength(2);
    expect(collectionIds.value).toContain(100);
    expect(collectionIds.value).toContain(200);
  });

  it("calls the unified container endpoint URL for FILE", async () => {
    mockFetchOk([]);
    useContainerReferencedByCollections(42, "FILE");
    await flush();
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string];
    expect(url).toContain("/v2/containers/42/linked-data-objects");
  });

  it("sends Authorization header with Bearer token", async () => {
    mockFetchOk([]);
    useContainerReferencedByCollections(42, "FILE");
    await flush();
    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("returns empty array (not null) on HTTP 403 — shows 0 not unsupported", async () => {
    mockFetchError(403);
    const { collectionIds, isLoading } = useContainerReferencedByCollections(10, "FILE");
    await flush();
    expect(isLoading.value).toBe(false);
    expect(collectionIds.value).toEqual([]);
  });

  it("returns empty array on network error", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Network down")));
    const { collectionIds, isLoading } = useContainerReferencedByCollections(10, "FILE");
    await flush();
    expect(isLoading.value).toBe(false);
    expect(collectionIds.value).toEqual([]);
  });
});

const TIMESERIES_APP_ID = "01901234-5678-7abc-def0-abcdef012345";

describe("useContainerReferencedByCollections — TIMESERIES container", () => {
  it("calls the unified container endpoint URL using containerAppId string", async () => {
    mockFetchOk([]);
    useContainerReferencedByCollections(TIMESERIES_APP_ID, "TIMESERIES");
    await flush();
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string];
    expect(url).toContain(`/v2/containers/${TIMESERIES_APP_ID}/linked-data-objects`);
  });

  it("deduplicates when the same collection references the container multiple times", async () => {
    const dataObjects = [
      { id: 1, collectionId: 300, referenceIds: [], successorIds: [], childrenIds: [], parentId: null, incomingIds: [], name: "A", createdAt: "2024-01-01", createdBy: "u1", updatedAt: null, updatedBy: null },
      { id: 2, collectionId: 300, referenceIds: [], successorIds: [], childrenIds: [], parentId: null, incomingIds: [], name: "B", createdAt: "2024-01-01", createdBy: "u1", updatedAt: null, updatedBy: null },
    ];
    mockFetchOk(dataObjects);
    const { collectionIds } = useContainerReferencedByCollections(TIMESERIES_APP_ID, "TIMESERIES");
    await flush();
    expect(collectionIds.value).toHaveLength(1);
    expect(collectionIds.value).toContain(300);
  });
});

describe("useContainerReferencedByCollections — STRUCTUREDDATA container", () => {
  it("calls the unified container endpoint URL for STRUCTUREDDATA", async () => {
    mockFetchOk([]);
    useContainerReferencedByCollections(77, "STRUCTUREDDATA");
    await flush();
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string];
    expect(url).toContain("/v2/containers/77/linked-data-objects");
  });
});
