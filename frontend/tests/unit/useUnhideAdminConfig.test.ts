import { describe, it, expect, vi, beforeEach } from "vitest";
import type { UnhideConfigIO } from "~/composables/context/admin/useUnhideAdminConfig";
import { useUnhideAdminConfig } from "~/composables/context/admin/useUnhideAdminConfig";

const ACCESS_TOKEN = "test-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("fetch", vi.fn());
});

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number, bodyText = "error") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

const defaultConfig: UnhideConfigIO = {
  enabled: false,
  feedPublic: false,
  contactEmail: null,
  harvestApiKeyMintedAt: null,
  harvestApiKeyFingerprint: null,
};

describe("useUnhideAdminConfig — refresh()", () => {
  it("loads config on mount", async () => {
    mockFetchOk(defaultConfig);
    const { config, isLoading } = useUnhideAdminConfig();

    // Loading starts true immediately
    expect(isLoading.value).toBe(true);
    await vi.runAllTimersAsync().catch(() => null);
    // Settle the fetch promise
    await Promise.resolve();
    await Promise.resolve();
  });

  it("sets config from successful GET", async () => {
    const data: UnhideConfigIO = { ...defaultConfig, enabled: true };
    mockFetchOk(data);
    const { config, error, refresh } = useUnhideAdminConfig();
    await refresh();

    expect(config.value).toEqual(data);
    expect(error.value).toBeNull();
  });

  it("sets error on HTTP failure", async () => {
    mockFetchError(403);
    const { config, error, refresh } = useUnhideAdminConfig();
    await refresh();

    expect(error.value).toBe("Failed to load Unhide config");
    expect(config.value).toBeNull();
  });

  it("sends Authorization header with Bearer token", async () => {
    mockFetchOk(defaultConfig);
    const { refresh } = useUnhideAdminConfig();
    await refresh();

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/unhide/config");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

describe("useUnhideAdminConfig — patch()", () => {
  it("sends PATCH with correct body and updates local config", async () => {
    const updated: UnhideConfigIO = { ...defaultConfig, enabled: true };
    mockFetchOk(updated);
    const { config, patch } = useUnhideAdminConfig();
    const result = await patch({ enabled: true });

    expect(result).toEqual(updated);
    expect(config.value).toEqual(updated);

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/unhide/config");
    expect(opts.method).toBe("PATCH");
    expect(JSON.parse(opts.body as string)).toEqual({ enabled: true });
  });

  it("returns null and sets error on PATCH failure", async () => {
    mockFetchError(400, JSON.stringify({ detail: "Field is read-only" }));
    const { error, patch } = useUnhideAdminConfig();
    const result = await patch({ enabled: false });

    expect(result).toBeNull();
    expect(error.value).toBe("Field is read-only");
  });

  it("returns null on network error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { error, patch } = useUnhideAdminConfig();
    const result = await patch({ feedPublic: true });

    expect(result).toBeNull();
    expect(error.value).toBe("Failed to save Unhide config");
  });
});
