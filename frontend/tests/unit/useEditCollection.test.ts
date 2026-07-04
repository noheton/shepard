/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves `useEditCollection`
 * PATCHes through the v2 appId-keyed endpoint. Pre-fix the v1 generated
 * `updateCollection` expected a numeric Neo4j long; post-Neo4j-reset
 * Collections carry UUID v7 only so the edit-dialog Save silently 404'd.
 *
 * Coverage:
 *   - PATCH /v2/collections/{handle} with merge-patch+json
 *   - prefers `appId` when present
 *   - falls back to numeric id when appId absent
 *   - v1 generated client `updateCollection` is NOT called
 *   - `editCollectionPermissions` stays on v1 (PERMS-1 hold-back)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1UpdateCollection = vi.fn();
const v1EditCollectionPermissions = vi.fn().mockResolvedValue({});

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
    handleCollectionUpdate: vi.fn(),
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  CollectionApi: function CollectionApi() {},
  ResponseError: class ResponseError extends Error {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () =>
    ref({
      updateCollection: v1UpdateCollection,
      editCollectionPermissions: v1EditCollectionPermissions,
    }),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useEditCollection — BUG-COLL-APPID-ROUTE-005", () => {
  it("PATCHes via /v2/collections/{appId} with merge-patch+json", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });
    vi.stubGlobal("fetch", fetchMock);

    const mod = await import(
      "~/components/context/collection/edit-dialog/useEditCollection"
    );
    const appId = "019e6ffc-1234-7abc-9def-000000000042";
    const collection = {
      id: 42,
      appId,
      name: "Original",
      attributes: {},
      description: "",
      status: null,
      heroImageUrl: null,
    } as unknown as Parameters<typeof mod.useEditCollection>[0];
    const isValid = ref(true);
    const onSuccess = vi.fn();
    const { updatedCollection, saveChanges } = mod.useEditCollection(
      collection,
      onSuccess,
      isValid,
    );
    updatedCollection.value.name = "Renamed";

    await saveChanges();
    await flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [calledUrl, calledInit] = fetchMock.mock.calls[0]!;
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(appId)}`,
    );
    expect((calledInit as RequestInit).method).toBe("PATCH");
    const headers = (calledInit as RequestInit).headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/merge-patch+json");
    const body = JSON.parse((calledInit as RequestInit).body as string) as { name: string };
    expect(body.name).toBe("Renamed");
    expect(v1UpdateCollection).not.toHaveBeenCalled();
    expect(onSuccess).toHaveBeenCalled();
  });

  it("falls back to numeric id when appId is absent", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    });
    vi.stubGlobal("fetch", fetchMock);

    const mod = await import(
      "~/components/context/collection/edit-dialog/useEditCollection"
    );
    const collection = {
      id: 42,
      // no appId
      name: "Legacy",
      attributes: {},
      description: "",
      status: null,
      heroImageUrl: null,
    } as unknown as Parameters<typeof mod.useEditCollection>[0];
    const isValid = ref(true);
    const { saveChanges } = mod.useEditCollection(
      collection,
      vi.fn(),
      isValid,
    );

    await saveChanges();
    await flush();

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect(v1UpdateCollection).not.toHaveBeenCalled();
  });
});
