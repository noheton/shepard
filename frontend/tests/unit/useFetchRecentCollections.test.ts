import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchRecentCollections } from "~/composables/context/useFetchRecentCollections";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { DataObjectAttributes } from "@dlr-shepard/backend-client";

// vi.mock is hoisted by Vitest above the imports at runtime.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

const mockGetAllCollections = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getAllCollections: mockGetAllCollections }),
  );
});

/** Flush the microtask queue so the auto-triggered fetch completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchRecentCollections", () => {
  it("starts with loading=true and empty collections", () => {
    mockGetAllCollections.mockResolvedValue([]);
    const { loading, collections } = useFetchRecentCollections();
    expect(loading.value).toBe(true);
    expect(collections.value).toEqual([]);
  });

  it("populates collections and clears loading on success", async () => {
    const data = [
      { id: 1, name: "A", dataObjectIds: [] },
      { id: 2, name: "B", dataObjectIds: [] },
    ];
    mockGetAllCollections.mockResolvedValue(data);

    const { loading, collections, error } = useFetchRecentCollections();
    await flush();

    expect(collections.value).toEqual(data);
    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("passes correct query params (page=0, size=30, orderDesc=true, orderBy=updatedAt)", async () => {
    mockGetAllCollections.mockResolvedValue([]);
    useFetchRecentCollections();
    await flush();

    expect(mockGetAllCollections).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 30,
        orderDesc: true,
        orderBy: DataObjectAttributes.UpdatedAt,
      }),
    );
  });

  it("sets error message and calls handleError when fetch throws", async () => {
    const err = new Error("Network error");
    mockGetAllCollections.mockRejectedValue(err);

    const { loading, error } = useFetchRecentCollections();
    await flush();

    expect(error.value).toBe("Could not load collections.");
    expect(loading.value).toBe(false);
    expect((globalThis as unknown as { handleError: ReturnType<typeof vi.fn> }).handleError)
      .toHaveBeenCalledWith(err, "fetching recent collections");
  });

  it("filters out CLOSED collections from the default view", async () => {
    const data = [
      { id: 1, name: "Open", status: "OPEN" },
      { id: 2, name: "Closed", status: "CLOSED" },
    ];
    mockGetAllCollections.mockResolvedValue(data);

    const { collections, allCollections, showClosed } = useFetchRecentCollections();
    await flush();

    expect(allCollections.value).toEqual(data);
    expect(collections.value).toEqual([{ id: 1, name: "Open", status: "OPEN" }]);

    // Toggling showClosed surfaces the CLOSED collection too.
    showClosed.value = true;
    expect(collections.value).toEqual(data);
  });

  it("refetch triggers a second API call and updates collections", async () => {
    mockGetAllCollections.mockResolvedValue([]);
    const { collections, refetch } = useFetchRecentCollections();
    await flush();

    const fresh = [{ id: 3, name: "C" }];
    mockGetAllCollections.mockResolvedValue(fresh);
    await refetch();

    expect(collections.value).toEqual(fresh);
    expect(mockGetAllCollections).toHaveBeenCalledTimes(2);
  });

  it("refetch resets error from a previous failure", async () => {
    mockGetAllCollections.mockRejectedValue(new Error("fail"));
    const { error, refetch } = useFetchRecentCollections();
    await flush();
    expect(error.value).not.toBeNull();

    mockGetAllCollections.mockResolvedValue([]);
    await refetch();
    expect(error.value).toBeNull();
  });
});
