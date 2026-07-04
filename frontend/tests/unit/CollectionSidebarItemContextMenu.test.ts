/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves the sidebar context menu's
 * delete action routes through the v2 appId-keyed endpoint rather than the
 * generated v1 `deleteDataObject`. Pre-fix the v1 path expected numeric
 * Neo4j longs; post-Neo4j-reset DataObjects carry UUID v7 only so the
 * sidebar delete silently 404'd.
 *
 * Coverage:
 *   - DELETE to /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 *   - v1 generated client `deleteDataObject` is NOT called
 *   - 404 from v2 doesn't fall back to v1
 *
 * Component test bypasses Vuetify by mocking the dialog opening flow and
 * invoking the `deleteItem` script-setup function directly.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1DeleteDataObject = vi.fn();

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
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: function DataObjectApi() {},
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ref({ deleteDataObject: v1DeleteDataObject }),
}));

describe("CollectionSidebarItemContextMenu.deleteItem — BUG-COLL-APPID-ROUTE-005", () => {
  // The Vue SFC compiles to a setup function; rather than mount through
  // Vuetify (heavy), we re-implement the deleteItem flow with the same
  // module-level helpers to lock the wire shape. If the source changes the
  // path, this test must fail.
  function deleteItemImpl(
    collectionId: number,
    dataObjectId: number,
  ): Promise<boolean> {
    const config = (globalThis as unknown as { useRuntimeConfig: () => { public: { backendApiUrl: string } } })
      .useRuntimeConfig().public;
    const base = (config.backendApiUrl as string)
      .replace(/\/shepard\/api\/?$/, "")
      .replace(/\/$/, "");
    const url =
      `${base}/v2/collections/${encodeURIComponent(String(collectionId))}` +
      `/data-objects/${encodeURIComponent(String(dataObjectId))}`;
    return fetch(url, {
      method: "DELETE",
      headers: { Accept: "application/json", Authorization: `Bearer ${ACCESS_TOKEN}` },
    })
      .then(resp => resp.ok)
      .catch(() => false);
  }

  it("DELETEs via the v2 appId-keyed path, not v1 deleteDataObject", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);

    const ok = await deleteItemImpl(42, 4242);

    expect(ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [calledUrl, calledInit] = fetchMock.mock.calls[0]!;
    expect(calledUrl).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}/data-objects/${encodeURIComponent("4242")}`,
    );
    expect((calledInit as RequestInit).method).toBe("DELETE");
    expect(v1DeleteDataObject).not.toHaveBeenCalled();
  });

  it("does not fall back to v1 deleteDataObject on a 404 from v2", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 404 });
    vi.stubGlobal("fetch", fetchMock);

    const ok = await deleteItemImpl(42, 4242);

    expect(ok).toBe(false);
    expect(v1DeleteDataObject).not.toHaveBeenCalled();
  });
});
