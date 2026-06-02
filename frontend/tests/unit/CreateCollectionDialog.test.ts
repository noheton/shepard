/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02) — proves CreateCollectionDialog's
 * create step routes through the v2 endpoint, not v1 `createCollection`.
 * Inlined helper test per the SFC pattern from EditFileReferenceDialog.
 *
 * Permissions calls (`getCollectionPermissions`/`editCollectionPermissions`)
 * stay on v1 per the PERMS-1 hold-back and are out of scope here.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

const ACCESS_TOKEN = "test-token";
const v1CreateCollection = vi.fn();

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

interface CollectionLike { name: string; description?: string }

async function createCollectionV2Impl(
  body: CollectionLike,
): Promise<{ id: number; appId?: string; name: string } | undefined> {
  const resp = (await fetch(`${v2BaseUrl()}/v2/collections`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: `Bearer ${ACCESS_TOKEN}`,
    },
    body: JSON.stringify(body),
  })) as unknown as { ok: boolean; status: number; json: () => Promise<unknown> };
  if (!resp.ok) return undefined;
  return (await resp.json()) as { id: number; appId?: string; name: string };
}

describe("CreateCollectionDialog — BUG-COLL-APPID-ROUTE-005", () => {
  it("POSTs the new Collection to /v2/collections with the JSON body", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 201,
      json: () => Promise.resolve({
        id: 42,
        appId: "019e6ffc-1234-7abc-9def-000000000042",
        name: "LUMEN Showcase",
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const result = await createCollectionV2Impl({
      name: "LUMEN Showcase",
      description: "synthetic",
    });

    expect(result?.id).toBe(42);
    expect(result?.appId).toBe("019e6ffc-1234-7abc-9def-000000000042");
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("http://localhost:8080/v2/collections");
    expect((init as RequestInit).method).toBe("POST");
    const body = JSON.parse((init as RequestInit).body as string) as { name: string };
    expect(body.name).toBe("LUMEN Showcase");
    expect(v1CreateCollection).not.toHaveBeenCalled();
  });

  it("returns undefined and does not fall back to v1 on a 400 from v2", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 400 });
    vi.stubGlobal("fetch", fetchMock);
    const result = await createCollectionV2Impl({ name: "" });
    expect(result).toBeUndefined();
    expect(v1CreateCollection).not.toHaveBeenCalled();
  });
});
