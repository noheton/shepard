import { describe, it, expect, vi, beforeEach } from "vitest";

// Must mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

import { useFetchRecentCollections } from "~/composables/context/useFetchRecentCollections";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { BasicCollectionAttributes } from "@dlr-shepard/backend-client";

const mockSearchCollections = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ searchCollections: mockSearchCollections }),
  );
});

/** Flush the microtask queue so the auto-triggered fetch completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchRecentCollections", () => {
  it("starts with loading=true and empty collections", () => {
    mockSearchCollections.mockResolvedValue({ results: [] });
    const { loading, collections } = useFetchRecentCollections();
    expect(loading.value).toBe(true);
    expect(collections.value).toEqual([]);
  });

  it("populates collections and clears loading on success", async () => {
    const data = [
      { id: 1, name: "A", dataObjectIds: [] },
      { id: 2, name: "B", dataObjectIds: [] },
    ];
    mockSearchCollections.mockResolvedValue({ results: data });

    const { loading, collections, error } = useFetchRecentCollections();
    await flush();

    expect(collections.value).toEqual(data);
    expect(loading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("passes correct query params (page=0, size=6, orderDesc=true, orderBy=updatedAt)", async () => {
    mockSearchCollections.mockResolvedValue({ results: [] });
    useFetchRecentCollections();
    await flush();

    expect(mockSearchCollections).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 6,
        orderDesc: true,
        orderBy: BasicCollectionAttributes.UpdatedAt,
      }),
    );
  });

  it("sets error message and calls handleError when fetch throws", async () => {
    const err = new Error("Network error");
    mockSearchCollections.mockRejectedValue(err);

    const { loading, error } = useFetchRecentCollections();
    await flush();

    expect(error.value).toBe("Could not load collections.");
    expect(loading.value).toBe(false);
    expect((globalThis as unknown as { handleError: ReturnType<typeof vi.fn> }).handleError)
      .toHaveBeenCalledWith(err, "fetching recent collections");
  });

  it("refetch triggers a second API call and updates collections", async () => {
    mockSearchCollections.mockResolvedValue({ results: [] });
    const { collections, refetch } = useFetchRecentCollections();
    await flush();

    const fresh = [{ id: 3, name: "C" }];
    mockSearchCollections.mockResolvedValue({ results: fresh });
    await refetch();

    expect(collections.value).toEqual(fresh);
    expect(mockSearchCollections).toHaveBeenCalledTimes(2);
  });

  it("refetch resets error from a previous failure", async () => {
    mockSearchCollections.mockRejectedValue(new Error("fail"));
    const { error, refetch } = useFetchRecentCollections();
    await flush();
    expect(error.value).not.toBeNull();

    mockSearchCollections.mockResolvedValue({ results: [] });
    await refetch();
    expect(error.value).toBeNull();
  });
});
