/**
 * CC1d — unit coverage for the collection-scoped container composable.
 *
 * Exercises:
 *  - starts with empty containers and loading=false
 *  - fetches containers when collectionAppId becomes non-null
 *  - filters file containers correctly (FILE vs TIMESERIES)
 *  - handles API errors gracefully
 *  - re-fetches when collectionAppId ref changes
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchCollectionContainers } from "~/composables/context/useFetchCollectionContainers";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// CollectionReferencedContainersApi is a /v2/ client, so the composable resolves it via
// the v2 helper (basePath = host without /shepard/api). The mock therefore
// targets useV2ShepardApi — mocking useShepardApi would never be hit.
// vi.mock is hoisted by Vitest above the imports at runtime.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListReferencedContainers = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listReferencedContainers: mockListReferencedContainers }),
  );
});

/** Flush the microtask queue so watch(immediate) fetch completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

const FILE_CONTAINER = {
  id: 1,
  appId: "file-app-id",
  name: "My Files",
  kind: "file" as const,
};
const TS_CONTAINER = {
  id: 2,
  appId: "ts-app-id",
  name: "My Timeseries",
  kind: "timeseries" as const,
};

describe("useFetchCollectionContainers", () => {
  it("starts with empty containers when appId is null", () => {
    mockListReferencedContainers.mockResolvedValue([]);
    const appId = ref<string | null>(null);
    const { containers, isLoading } = useFetchCollectionContainers(appId);
    // null appId — no fetch triggered
    expect(mockListReferencedContainers).not.toHaveBeenCalled();
    expect(containers.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });

  it("fetches containers when appId is non-null on init", async () => {
    mockListReferencedContainers.mockResolvedValue([FILE_CONTAINER, TS_CONTAINER]);
    const appId = ref<string | null>("coll-app-id");
    const { containers, isLoading } = useFetchCollectionContainers(appId);
    await flush();

    expect(mockListReferencedContainers).toHaveBeenCalledWith({
      collectionAppId: "coll-app-id",
    });
    expect(containers.value).toHaveLength(2);
    expect(isLoading.value).toBe(false);
  });

  it("file containers are present in raw list, caller can filter by containerType", async () => {
    mockListReferencedContainers.mockResolvedValue([FILE_CONTAINER, TS_CONTAINER]);
    const appId = ref<string | null>("coll-app-id");
    const { containers } = useFetchCollectionContainers(appId);
    await flush();

    const fileOnly = containers.value.filter(c => c.kind === "file");
    expect(fileOnly).toHaveLength(1);
    expect(fileOnly[0]?.name).toBe("My Files");
  });

  it("re-fetches when collectionAppId changes", async () => {
    mockListReferencedContainers.mockResolvedValue([FILE_CONTAINER]);
    const appId = ref<string | null>("coll-a");
    const { containers } = useFetchCollectionContainers(appId);
    await flush();
    expect(containers.value).toHaveLength(1);

    const newContainer = { ...FILE_CONTAINER, id: 99, name: "Other Files" };
    mockListReferencedContainers.mockResolvedValue([newContainer]);
    appId.value = "coll-b";
    await flush();

    expect(mockListReferencedContainers).toHaveBeenCalledTimes(2);
    expect(mockListReferencedContainers).toHaveBeenLastCalledWith({
      collectionAppId: "coll-b",
    });
    expect(containers.value[0]?.name).toBe("Other Files");
  });

  it("does not fetch when appId changes to null", async () => {
    mockListReferencedContainers.mockResolvedValue([FILE_CONTAINER]);
    const appId = ref<string | null>("coll-a");
    const { containers } = useFetchCollectionContainers(appId);
    await flush();
    expect(mockListReferencedContainers).toHaveBeenCalledTimes(1);

    appId.value = null;
    await flush();
    // No second call
    expect(mockListReferencedContainers).toHaveBeenCalledTimes(1);
    // Previous data preserved
    expect(containers.value).toHaveLength(1);
  });

  it("leaves containers empty and clears loading on API error", async () => {
    mockListReferencedContainers.mockRejectedValue(new Error("500"));
    const appId = ref<string | null>("coll-app-id");
    const { containers, isLoading } = useFetchCollectionContainers(appId);
    await flush();

    expect(containers.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });
});
