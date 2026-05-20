import { describe, it, expect, vi, beforeEach } from "vitest";
import type { PayloadVersionIO } from "~/composables/container/useFetchPayloadVersions";
import { useFetchPayloadVersions } from "~/composables/container/useFetchPayloadVersions";

const ACCESS_TOKEN = "test-token";

beforeEach(() => {
  vi.clearAllMocks();
  // Authenticated session by default
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  // Reset fetch mock
  vi.stubGlobal("fetch", vi.fn());
});

const mockVersion = (n: number): PayloadVersionIO => ({
  appId: `app-id-${n}`,
  versionNumber: n,
  fileOid: `oid-${n}`,
  sha256: "DEADBEEF".repeat(8),
  sizeBytes: 1024 * n,
  uploadedBy: "user@test",
  uploadedAt: "2026-05-20T10:00:00Z",
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: true,
    json: () => Promise.resolve(body),
  }));
}

function mockFetchError(status: number, text = "error") {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
    ok: false,
    status,
    text: () => Promise.resolve(text),
  }));
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchPayloadVersions", () => {
  it("starts with empty versions and not loading", () => {
    mockFetchOk([]);
    const { versions, isLoading, error } = useFetchPayloadVersions("app-id", "file.csv");
    expect(versions.value).toEqual([]);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();
  });

  it("does NOT call fetch until load() is invoked", () => {
    const spy = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    vi.stubGlobal("fetch", spy);
    useFetchPayloadVersions("app-id", "file.csv");
    expect(spy).not.toHaveBeenCalled();
  });

  it("sets isLoading to true while fetch is in progress", async () => {
    let resolve!: (v: unknown) => void;
    vi.stubGlobal("fetch", vi.fn().mockReturnValue(new Promise(r => { resolve = r; })));

    const { isLoading, load } = useFetchPayloadVersions("app-id", "file.csv");
    const loadPromise = load();
    expect(isLoading.value).toBe(true);

    resolve({ ok: true, json: () => Promise.resolve([]) });
    await loadPromise;
    expect(isLoading.value).toBe(false);
  });

  it("populates versions on success and sends correct URL + auth header", async () => {
    const data = [mockVersion(1), mockVersion(2)];
    mockFetchOk(data);

    const { versions, isLoading, error, load } = useFetchPayloadVersions(
      "container-app-id",
      "my file.csv",
    );
    await load();
    await flush();

    expect(versions.value).toEqual(data);
    expect(isLoading.value).toBe(false);
    expect(error.value).toBeNull();

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/v2/file-containers/container-app-id/files/");
    expect(url).toContain(encodeURIComponent("my file.csv"));
    expect(url).toContain("/versions");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("sets error message on HTTP error response", async () => {
    mockFetchError(403, "Forbidden");

    const { versions, error, load } = useFetchPayloadVersions("app-id", "file.csv");
    await load();

    expect(error.value).toMatch(/403/);
    expect(versions.value).toEqual([]);
  });

  it("sets error on network failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Network down")));

    const { error, load } = useFetchPayloadVersions("app-id", "file.csv");
    await load();

    expect(error.value).toBe("Network down");
  });

  it("resets error and versions on a successful reload after failure", async () => {
    mockFetchError(500);
    const { versions, error, load } = useFetchPayloadVersions("app-id", "file.csv");
    await load();
    expect(error.value).not.toBeNull();

    const fresh = [mockVersion(1)];
    mockFetchOk(fresh);
    await load();

    expect(error.value).toBeNull();
    expect(versions.value).toEqual(fresh);
  });

  it("sets error when not authenticated", async () => {
    (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
      data: ref<{ accessToken: string } | null>(null),
    });

    const { error, load } = useFetchPayloadVersions("app-id", "file.csv");
    await load();

    expect(error.value).toMatch(/authenticated/i);
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});
