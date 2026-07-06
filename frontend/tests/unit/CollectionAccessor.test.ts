/**
 * BUG-COLL-APPID-ROUTE-003 (2026-06-02) — proves `CollectionAccessor` routes
 * GET / DELETE / PATCH through the v2 appId-keyed endpoint
 * `/v2/collections/{collectionAppId}` rather than the generated v1 client.
 * BUG-COLL-APPID-ROUTE-PERMS-1 (2026-07-06) — proves permissions / roles now
 * use the v2 `CollectionPermissionsApi` keyed by appId, not v1 numeric id.
 * Coverage:
 *   - fetchData() GETs via v2 (no `getCollection` on v1)
 *   - delete() DELETEs via v2 (no `deleteCollection` on v1)
 *   - updateCollection() PATCHes via v2 with merge-patch headers
 *   - fetchPermissions() uses v2 CollectionPermissionsApi.getCollectionPermissions({appId})
 *   - fetchRoles() uses v2 CollectionPermissionsApi.getCollectionRoles({appId})
 *   - fetchPermissions() / fetchRoles() short-circuit fail-soft when appId is absent
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";

const v2GetCollectionPermissions = vi.fn();
const v2GetCollectionRoles = vi.fn();
const v2EditCollectionPermissions = vi.fn();
const mockUseV2ShepardApi = vi.fn(() =>
  ref({
    getCollectionPermissions: v2GetCollectionPermissions,
    getCollectionRoles: v2GetCollectionRoles,
    editCollectionPermissions: v2EditCollectionPermissions,
  }),
);
const routerPush = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  v2GetCollectionPermissions.mockResolvedValue({ permissionType: "PRIVATE" });
  v2GetCollectionRoles.mockResolvedValue({ owner: true, writer: false, reader: true, manager: false });
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
  CollectionPermissionsApi: function CollectionPermissionsApi() {},
  UserApi: function UserApi() {},
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => mockUseV2ShepardApi(),
}));

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const APP_ID = "019e6ffc-1234-7abc-9def-000000000042";
const collectionBody = {
  id: 42,
  appId: APP_ID,
  name: "LUMEN Showcase",
  description: "synthetic",
};
// Missing appId — permissions/roles must short-circuit without calling the API.
const collectionBodyNoAppId = {
  id: 42,
  appId: null as string | null,
  name: "Legacy Collection",
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
    const acc = new CollectionAccessor(APP_ID);
    await acc.fetchData();
    await flush();

    expect(fetch).toHaveBeenCalledTimes(1);
    const calledUrl = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0];
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(APP_ID)}`,
    );
    expect(acc.collection.value?.name).toBe("LUMEN Showcase");
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
    const acc = new CollectionAccessor(APP_ID);
    await acc.delete();
    await flush();

    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    // First a GET (fetchData), then the DELETE.
    expect(calls.length).toBeGreaterThanOrEqual(2);
    const deleteCall = calls.find(c => (c[1] as RequestInit).method === "DELETE");
    expect(deleteCall).toBeTruthy();
    expect(deleteCall?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(APP_ID)}`,
    );
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
    const acc = new CollectionAccessor(APP_ID);
    await acc.updateCollection({ ...collectionBody, name: "Renamed" } as never);
    await flush();

    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    expect(calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(APP_ID)}`,
    );
    expect((calls[0]?.[1] as RequestInit).method).toBe("PATCH");
    const headers = (calls[0]?.[1] as RequestInit).headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/merge-patch+json");
  });

  it("fetchPermissions uses v2 CollectionPermissionsApi keyed by appId", async () => {
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
    const acc = new CollectionAccessor(APP_ID);
    await acc.fetchPermissions();
    await flush();

    expect(v2GetCollectionPermissions).toHaveBeenCalledWith({ appId: APP_ID });
    expect(acc.permissions.value).toEqual({ permissionType: "PRIVATE" });
  });

  it("fetchPermissions fails-soft when Collection has no appId", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBodyNoAppId),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(APP_ID);
    await acc.fetchPermissions();
    await flush();

    expect(v2GetCollectionPermissions).not.toHaveBeenCalled();
    expect(acc.permissions.value).toBeUndefined();
  });

  it("fetchRoles uses v2 CollectionPermissionsApi keyed by appId", async () => {
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
    const acc = new CollectionAccessor(APP_ID);
    await acc.fetchRoles();
    await flush();

    expect(v2GetCollectionRoles).toHaveBeenCalledWith({ appId: APP_ID });
    expect(acc.roles.value).toEqual({ owner: true, writer: false, reader: true, manager: false });
  });

  it("fetchRoles fails-soft when Collection has no appId", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(collectionBodyNoAppId),
      }),
    );
    const { CollectionAccessor } = await import(
      "~/composables/context/CollectionAccessor"
    );
    const acc = new CollectionAccessor(APP_ID);
    await acc.fetchRoles();
    await flush();

    expect(v2GetCollectionRoles).not.toHaveBeenCalled();
    expect(acc.roles.value).toBeUndefined();
  });
});
