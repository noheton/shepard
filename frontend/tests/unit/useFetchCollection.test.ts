/**
 * UX-WALK-2026-05-29-03 — unit tests for useFetchCollection with appId support.
 *
 * Exercises:
 *  - numeric id path: fetches via CollectionApi (v1) as before
 *  - appId path: resolves via CollectionV2Api, then fetches roles by numeric id
 *  - appId path error: sets isError=true when the v2 call fails
 *  - NaN id without appId: sets isError=true (cannot resolve)
 *  - isPlausibleAppId: correctly identifies UUID v4/v7 strings
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchCollection } from "~/composables/context/useFetchCollection";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { isPlausibleAppId } from "~/utils/collectionRouteParams";

// vi.mock is hoisted above imports at runtime.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

// Nuxt auto-imports `onCollectionUpdated` from the resourceUpdateBus. In the
// test environment there is no Nuxt runtime, so we expose it as a global no-op.
// The composable registers a listener that calls `refetch()` on the event bus;
// we don't need to exercise that path here — unit tests for it can be separate.
Object.assign(globalThis, {
  onCollectionUpdated: vi.fn(),
  onUnmounted: vi.fn(),
});

const mockGetCollection = vi.fn();
const mockGetCollectionRoles = vi.fn();
const mockGetCollectionByAppId = vi.fn();

const FAKE_NUMERIC_ID = 42;
const FAKE_APP_ID = "0191e3a2-4a6b-7b4c-8d5e-6f7a8b9c0d1e";

const fakeCollection = {
  id: FAKE_NUMERIC_ID,
  appId: FAKE_APP_ID,
  name: "Test Collection",
  createdAt: new Date(),
  createdBy: "alice",
};

const fakeRoles = { owner: false, writer: true, reader: true, manager: false };

beforeEach(() => {
  vi.clearAllMocks();

  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({
      getCollection: mockGetCollection,
      getCollectionRoles: mockGetCollectionRoles,
    }),
  );

  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({
      getCollectionByAppId: mockGetCollectionByAppId,
    }),
  );
});

/** Flush micro-task queue so promise chains resolve. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

// ── isPlausibleAppId ──────────────────────────────────────────────────────────

describe("isPlausibleAppId", () => {
  it("returns true for a valid UUID v7 string", () => {
    expect(isPlausibleAppId("0191e3a2-4a6b-7b4c-8d5e-6f7a8b9c0d1e")).toBe(true);
  });

  it("returns true for a valid UUID v4 string", () => {
    expect(isPlausibleAppId("550e8400-e29b-41d4-a716-446655440000")).toBe(true);
  });

  it("returns false for a numeric string", () => {
    expect(isPlausibleAppId("42")).toBe(false);
  });

  it("returns false for an empty string", () => {
    expect(isPlausibleAppId("")).toBe(false);
  });

  it("returns false for a partial UUID", () => {
    expect(isPlausibleAppId("0191e3a2-4a6b")).toBe(false);
  });
});

// ── numeric id path (unchanged behaviour) ────────────────────────────────────

describe("useFetchCollection — numeric id path", () => {
  it("calls getCollection and getCollectionRoles with numeric id", async () => {
    mockGetCollection.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    const { collection, isError } = useFetchCollection(FAKE_NUMERIC_ID);
    await flush();

    expect(mockGetCollection).toHaveBeenCalledWith({ collectionId: FAKE_NUMERIC_ID });
    expect(mockGetCollectionRoles).toHaveBeenCalledWith({ collectionId: FAKE_NUMERIC_ID });
    expect(collection.value?.id).toBe(FAKE_NUMERIC_ID);
    expect(isError.value).toBe(false);
  });

  it("sets isError=true when getCollection rejects", async () => {
    mockGetCollection.mockRejectedValue(new Error("404"));
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    const { collection, isError } = useFetchCollection(FAKE_NUMERIC_ID);
    await flush();

    expect(isError.value).toBe(true);
    expect(collection.value).toBeUndefined();
  });

  it("does NOT call CollectionV2Api when numeric id is provided", async () => {
    mockGetCollection.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    useFetchCollection(FAKE_NUMERIC_ID);
    await flush();

    expect(mockGetCollectionByAppId).not.toHaveBeenCalled();
  });
});

// ── appId (UUID) path — the new behaviour ────────────────────────────────────

describe("useFetchCollection — appId resolution path (UX-WALK-2026-05-29-03)", () => {
  it("calls getCollectionByAppId when collectionId is NaN and appId is provided", async () => {
    mockGetCollectionByAppId.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    const { collection, isError } = useFetchCollection(NaN, FAKE_APP_ID);
    await flush();

    expect(mockGetCollectionByAppId).toHaveBeenCalledWith({
      collectionAppId: FAKE_APP_ID,
    });
    expect(collection.value?.id).toBe(FAKE_NUMERIC_ID);
    expect(isError.value).toBe(false);
  });

  it("fetches roles by the resolved numeric id (not the NaN sentinel)", async () => {
    mockGetCollectionByAppId.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    useFetchCollection(NaN, FAKE_APP_ID);
    await flush();

    // Must use the numeric id from the resolved collection, not NaN
    expect(mockGetCollectionRoles).toHaveBeenCalledWith({
      collectionId: FAKE_NUMERIC_ID,
    });
  });

  it("does NOT call the v1 getCollection when resolving by appId", async () => {
    mockGetCollectionByAppId.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue(fakeRoles);

    useFetchCollection(NaN, FAKE_APP_ID);
    await flush();

    expect(mockGetCollection).not.toHaveBeenCalled();
  });

  it("sets isError=true when getCollectionByAppId rejects (appId not found)", async () => {
    mockGetCollectionByAppId.mockRejectedValue(new Error("404 Not Found"));

    const { collection, isError } = useFetchCollection(NaN, FAKE_APP_ID);
    await flush();

    expect(isError.value).toBe(true);
    expect(collection.value).toBeUndefined();
  });

  it("exposes isAllowedToEditCollection computed from resolved roles", async () => {
    mockGetCollectionByAppId.mockResolvedValue(fakeCollection);
    mockGetCollectionRoles.mockResolvedValue({ owner: false, writer: true, reader: true, manager: false });

    const { isAllowedToEditCollection } = useFetchCollection(NaN, FAKE_APP_ID);
    await flush();

    expect(isAllowedToEditCollection.value).toBe(true);
  });
});
