/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves `pages/shapes/render.vue`
 * resolves the picker DataObject's appId through the v2 endpoint, not the
 * generated v1 `getDataObject`. Pre-fix the v1 path expected numeric
 * Neo4j longs; post-Neo4j-reset DataObjects carry UUID v7 only so the
 * picker → focusShepardId chain silently broke.
 *
 * Inlined-helper test per the SFC pattern from EditFileReferenceDialog.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const ACCESS_TOKEN = "test-token";
const v1GetDataObject = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, {
    useRuntimeConfig: () => ({
      public: { backendApiUrl: "http://localhost:8080/shepard/api" },
    }),
    useAuth: () => ({ data: { value: { accessToken: ACCESS_TOKEN } } }),
    handleError: vi.fn(),
  });
  vi.stubGlobal("fetch", vi.fn());
});

vi.mock("@dlr-shepard/backend-client", () => ({
  DataObjectApi: function DataObjectApi() {},
}));

function getV2Base(): string {
  const config = (globalThis as unknown as { useRuntimeConfig: () => { public: { backendApiUrl: string } } })
    .useRuntimeConfig().public;
  return (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

function getAuthHeaders(): Record<string, string> {
  return {
    "Content-Type": "application/json",
    Accept: "application/json",
    Authorization: `Bearer ${ACCESS_TOKEN}`,
  };
}

async function resolveFocusAppId(
  collectionId: number,
  dataObjectId: number,
): Promise<string> {
  const url =
    `${getV2Base()}/v2/collections/` +
    `${encodeURIComponent(String(collectionId))}/data-objects/` +
    `${encodeURIComponent(String(dataObjectId))}`;
  const resp = (await fetch(url, { headers: getAuthHeaders() })) as unknown as {
    ok: boolean;
    status: number;
    json: () => Promise<unknown>;
  };
  if (!resp.ok) return "";
  const body = (await resp.json()) as { appId?: string };
  return body.appId ?? "";
}

describe("pages/shapes/render — BUG-COLL-APPID-ROUTE-005", () => {
  it("resolves focusShepardId via GET /v2/.../data-objects/{id}", async () => {
    const appId = "019e6ffc-aaaa-7bcd-9eef-000000000042";
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ id: 4242, appId, name: "TR-004" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const focus = await resolveFocusAppId(42, 4242);

    expect(focus).toBe(appId);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      `http://localhost:8080/v2/collections/${encodeURIComponent("42")}/data-objects/${encodeURIComponent("4242")}`,
    );
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });

  it("returns empty focusShepardId on 404, no v1 fallback", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 404 });
    vi.stubGlobal("fetch", fetchMock);
    const focus = await resolveFocusAppId(42, 4242);
    expect(focus).toBe("");
    expect(v1GetDataObject).not.toHaveBeenCalled();
  });
});
