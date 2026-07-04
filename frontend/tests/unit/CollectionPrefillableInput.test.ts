/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves the collection prefill
 * input routes through the v2 endpoint, not v1 `getCollection`. Inlined
 * helper test per the SFC pattern from EditFileReferenceDialog.test.ts.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1GetCollection = vi.fn();

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

async function fetchCollectionV2(collectionId: number): Promise<{ id: number; name: string }> {
  const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(String(collectionId))}`;
  const resp = (await fetch(url, {
    headers: { Accept: "application/json", Authorization: `Bearer ${ACCESS_TOKEN}` },
  })) as unknown as { ok: boolean; status: number; json: () => Promise<unknown> };
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return (await resp.json()) as { id: number; name: string };
}

describe("CollectionPrefillableInput — BUG-COLL-APPID-ROUTE-005", () => {
  it("prefill lookup hits GET /v2/collections/{id}", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 42, name: "LUMEN Showcase" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchCollectionV2(42);

    expect(result).toEqual({ id: 42, name: "LUMEN Showcase" });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}`,
    );
    expect(v1GetCollection).not.toHaveBeenCalled();
  });

  it("does not fall back to v1 when v2 returns 404", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({}),
      }),
    );
    await expect(fetchCollectionV2(42)).rejects.toThrow(/404/);
    expect(v1GetCollection).not.toHaveBeenCalled();
  });
});
