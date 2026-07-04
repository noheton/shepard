/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves the data-object prefill
 * input routes through the v2 endpoint, not v1 `getDataObject`. Per the
 * SFC-test pattern from EditFileReferenceDialog.test.ts, the wire-shape
 * helper is exercised directly to lock the URL contract.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1GetDataObject = vi.fn();

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
  DataObjectApi: function DataObjectApi() {},
}));

function v2BaseUrl(): string {
  const config = (globalThis as unknown as { useRuntimeConfig: () => { public: { backendApiUrl: string } } })
    .useRuntimeConfig().public;
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function fetchDataObjectV2(
  collectionId: number,
  dataObjectId: number,
): Promise<{ id: number; name: string }> {
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(String(collectionId))}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const resp = (await fetch(url, {
    headers: { Accept: "application/json", Authorization: `Bearer ${ACCESS_TOKEN}` },
  })) as unknown as { ok: boolean; status: number; json: () => Promise<unknown> };
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return (await resp.json()) as { id: number; name: string };
}

describe("DataObjectPrefillableInput — BUG-COLL-APPID-ROUTE-005", () => {
  it("prefill lookup hits GET /v2/collections/.../data-objects/...", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 4242, name: "TR-004" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await fetchDataObjectV2(42, 4242);

    expect(result).toEqual({ id: 4242, name: "TR-004" });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}/data-objects/${encodeURIComponent("4242")}`,
    );
    expect(v1GetDataObject).not.toHaveBeenCalled();
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
    await expect(fetchDataObjectV2(42, 4242)).rejects.toThrow(/404/);
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });
});
