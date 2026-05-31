/**
 * SNAPSHOT-LIST-1-FE — composable tests for `useSnapshotList`.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSnapshotList } from "~/composables/useSnapshotList";

const ACCESS_TOKEN = "test-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
      text: () => Promise.resolve(JSON.stringify(body)),
    }),
  );
}

function mockFetchError(status: number, bodyText = "boom") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

describe("useSnapshotList — fetchPage()", () => {
  it("hits GET /v2/snapshots with default page=0 size=200", async () => {
    mockFetchOk({ items: [], total: 0, page: 0, size: 200 });
    const { fetchPage } = useSnapshotList();
    await fetchPage();
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("/v2/snapshots?");
    expect(url).toContain("page=0");
    expect(url).toContain("size=200");
  });

  it("populates items + total from the response envelope", async () => {
    mockFetchOk({
      items: [
        {
          appId: "s1",
          name: "v1.0",
          createdAt: "2026-05-30T12:00:00Z",
          collectionAppId: "c1",
          collectionName: "LUMEN",
        },
        {
          appId: "s2",
          name: "v1.1",
          createdAt: "2026-05-31T08:00:00Z",
          collectionAppId: "c1",
          collectionName: "LUMEN",
        },
      ],
      total: 2,
      page: 0,
      size: 200,
    });
    const { fetchPage, items, total } = useSnapshotList();
    const list = await fetchPage();
    expect(list.length).toBe(2);
    expect(items.value.length).toBe(2);
    expect(items.value[0]!.name).toBe("v1.0");
    expect(items.value[0]!.collectionName).toBe("LUMEN");
    expect(total.value).toBe(2);
  });

  it("includes collectionAppId param when provided", async () => {
    mockFetchOk({ items: [], total: 0, page: 0, size: 50 });
    const { fetchPage } = useSnapshotList();
    await fetchPage({ collectionAppId: "coll-abc", page: 1, size: 50 });
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("collectionAppId=coll-abc");
    expect(url).toContain("page=1");
    expect(url).toContain("size=50");
  });

  it("sends Bearer auth header when token present", async () => {
    mockFetchOk({ items: [], total: 0, page: 0, size: 200 });
    const { fetchPage } = useSnapshotList();
    await fetchPage();
    const [, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("surfaces error message on non-2xx and keeps items unchanged", async () => {
    mockFetchError(401, "unauthorised");
    const { fetchPage, error, items } = useSnapshotList();
    await fetchPage();
    expect(error.value).toContain("401");
    expect(items.value).toEqual([]);
  });

  it("handles missing items array gracefully", async () => {
    mockFetchOk({ total: 0, page: 0, size: 200 });
    const { fetchPage, items } = useSnapshotList();
    await fetchPage();
    expect(items.value).toEqual([]);
  });
});
