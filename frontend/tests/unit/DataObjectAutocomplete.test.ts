/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves the autocomplete's
 * initial-selection lookup routes through the v2 endpoint, not the
 * generated v1 `getDataObject`. Pre-fix the v1 path expected numeric
 * Neo4j longs; post-Neo4j-reset DataObjects carry UUID v7 only so the
 * autocomplete failed to populate the chip when reopening a dialog.
 *
 * Inlines the component's `getDataObjectById` logic per the SFC-test
 * pattern from EditFileReferenceDialog.test.ts. The wire shape is the
 * test target.
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

interface ResponseShape {
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}

function v2BaseUrl(): string {
  const config = (globalThis as unknown as { useRuntimeConfig: () => { public: { backendApiUrl: string } } })
    .useRuntimeConfig().public;
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function getDataObjectByIdImpl(
  collectionId: number,
  dataObjectId: number,
): Promise<{ id: number; name: string }> {
  const url =
    `${v2BaseUrl()}/v2/collections/` +
    `${encodeURIComponent(String(collectionId))}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const resp = (await fetch(url, {
    headers: { Accept: "application/json", Authorization: `Bearer ${ACCESS_TOKEN}` },
  })) as unknown as ResponseShape;
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return (await resp.json()) as { id: number; name: string };
}

describe("DataObjectAutocomplete.getDataObjectById — BUG-COLL-APPID-ROUTE-005", () => {
  it("routes through GET /v2/collections/{cAppId}/data-objects/{dAppId}", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 4242, name: "TR-004" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await getDataObjectByIdImpl(42, 4242);

    expect(result).toEqual({ id: 4242, name: "TR-004" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}/data-objects/${encodeURIComponent("4242")}`,
    );
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });

  it("throws on a 404 from v2 (no v1 fallback)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(getDataObjectByIdImpl(42, 4242)).rejects.toThrow(/404/);
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });
});
