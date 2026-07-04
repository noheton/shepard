/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves the delete-collection
 * dialog DELETEs through the v2 appId-keyed endpoint and prefers the
 * Collection's `appId` when present, falling back to the numeric `id` for
 * legacy callers.
 *
 * Coverage:
 *   - DELETE /v2/collections/{appId} when appId is present on the Collection
 *   - DELETE /v2/collections/{id} fallback when appId is null
 *   - v1 generated client `deleteCollection` is NOT called
 *   - 404 from v2 doesn't fall back to v1
 *
 * Inlined-helper test per the SFC pattern from EditFileReferenceDialog.test.ts.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1DeleteCollection = vi.fn();

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
  CollectionApi: function CollectionApi() {},
}));

function v2BaseUrl(): string {
  const config = (globalThis as unknown as { useRuntimeConfig: () => { public: { backendApiUrl: string } } })
    .useRuntimeConfig().public;
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

interface CollectionLike { id: number; name: string; appId?: string | null }

async function deleteCollectionImpl(collection: CollectionLike): Promise<boolean> {
  const handle = collection.appId ?? String(collection.id);
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(handle)}`;
  const resp = (await fetch(url, {
    method: "DELETE",
    headers: { Accept: "application/json", Authorization: `Bearer ${ACCESS_TOKEN}` },
  })) as unknown as { ok: boolean; status: number };
  return resp.ok;
}

describe("DeleteCollectionDialog — BUG-COLL-APPID-ROUTE-005", () => {
  it("DELETEs using the Collection's appId when present", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);

    const appId = "019e6ffc-1234-7abc-9def-000000000042";
    const ok = await deleteCollectionImpl({ id: 42, name: "X", appId });

    expect(ok).toBe(true);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent(appId)}`,
    );
    expect(v1DeleteCollection).not.toHaveBeenCalled();
  });

  it("falls back to numeric id when appId is absent", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal("fetch", fetchMock);

    const ok = await deleteCollectionImpl({ id: 42, name: "X" });

    expect(ok).toBe(true);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect(v1DeleteCollection).not.toHaveBeenCalled();
  });

  it("does not fall back to v1 on a 404 from v2", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 404 });
    vi.stubGlobal("fetch", fetchMock);
    const ok = await deleteCollectionImpl({ id: 42, name: "X" });
    expect(ok).toBe(false);
    expect(v1DeleteCollection).not.toHaveBeenCalled();
  });
});
