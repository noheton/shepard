/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02) — proves `CollectionAccessor` routes
 * GET / DELETE / PATCH through the v2 appId-keyed endpoint
 * `/v2/collections/{collectionAppId}` rather than the generated v1 client.
 * Coverage:
 *   - fetchData() GETs via v2 (no `getCollection` on v1)
 *   - delete() DELETEs via v2 (no `deleteCollection` on v1)
 *   - updateCollection() PATCHes via v2 with merge-patch headers
 *   - fetchPermissions() / fetchRoles() still use v1 (the documented hold-back)
 *     and look up by the numeric `id` carried in the v2 GET response
 *   - fetchPermissions() / fetchRoles() short-circuit fail-soft when the
 *     Collection has no numeric id (post-Neo4j-reset case)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

const v1GetCollectionSentinel = vi.fn();
const v1DeleteCollectionSentinel = vi.fn();
const v1UpdateCollectionSentinel = vi.fn();
const v1GetCollectionPermissions = vi.fn();
const v1GetCollectionRoles = vi.fn();
const v1EditCollectionPermissions = vi.fn();
const routerPush = vi.fn();

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
    useRouter: () => ({ push: routerPush }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("#imports", () => ({
  collectionsPath: "/collections",
}));
vi.mock("@dlr-shepard/backend-client", () => ({
  CollectionApi: function CollectionApi() {},
  UserApi: function UserApi() {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () =>
    ref({
      getCollection: v1GetCollectionSentinel,
      deleteCollection: v1DeleteCollectionSentinel,
      updateCollection: v1UpdateCollectionSentinel,
      getCollectionPermissions: v1GetCollectionPermissions,
      getCollectionRoles: v1GetCollectionRoles,
      editCollectionPermissions: v1EditCollectionPermissions,
    }),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const APP_ID = "019e6ffc-1234-7abc-9def-000000000042";
const collectionBody = {
  id: 42,
  appId: APP_ID,
  name: "LUMEN Showcase",
  description: "synthetic",
};
const collectionBodyNoNumericId = {
  id: null,
  appId: APP_ID,
  name: "Post-reset Collection",
};

describe("CollectionAccessor — BUG-COLL-APPID-ROUTE-003", () => {
  it("fetchData GETs via the v2 appId-keyed endpoint, not v1 getCollection", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBody),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.fetchData();
    await flush();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect(acc.collection.value?.name).toBe("LUMEN Showcase");
    expect(v1GetCollectionSentinel).not.toHaveBeenCalled();
  });

  it("delete() DELETEs via the v2 path", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation((_url: string, init: RequestInit) =>
        Promise.resolve({
          ok: true,
          status: init.method === "DELETE" ? 204 : 200,
          json: () => Promise.resolve(collectionBody),
        }),
      ),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.delete();
    await flush();

    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    // First a GET (fetchData), then the DELETE.
    expect(calls.length).toBeGreaterThanOrEqual(2);
    const deleteCall = calls.find(c => (c[1] as RequestInit).method === "DELETE");
    expect(deleteCall).toBeTruthy();
    expect(deleteCall?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect(v1DeleteCollectionSentinel).not.toHaveBeenCalled();
    expect(routerPush).toHaveBeenCalledWith("/collections");
  });

  it("updateCollection() PATCHes via the v2 path with merge-patch+json", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBody),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.updateCollection({ ...collectionBody, name: "Renamed" } as never);
    await flush();

    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    expect(calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect((calls[0]?.[1] as RequestInit).method).toBe("PATCH");
    const headers = (calls[0]?.[1] as RequestInit).headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/merge-patch+json");
    expect(v1UpdateCollectionSentinel).not.toHaveBeenCalled();
  });

  it("fetchPermissions uses v1 with the numeric id read off the v2 response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBody),
      }),
    );
    v1GetCollectionPermissions.mockResolvedValue({ owner: "alice" });
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.fetchPermissions();
    await flush();

    expect(v1GetCollectionPermissions).toHaveBeenCalledWith({ collectionId: 42 });
    expect(acc.permissions.value).toEqual({ owner: "alice" });
  });

  it("fetchPermissions fails-soft when v2 response has no numeric id", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBodyNoNumericId),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.fetchPermissions();
    await flush();

    expect(v1GetCollectionPermissions).not.toHaveBeenCalled();
    expect(acc.permissions.value).toBeUndefined();
  });

  it("fetchRoles fails-soft when v2 response has no numeric id", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBodyNoNumericId),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(42);
    await acc.fetchRoles();
    await flush();

    expect(v1GetCollectionRoles).not.toHaveBeenCalled();
    expect(acc.roles.value).toBeUndefined();
  });
});
