import { describe, it, expect, vi, beforeEach } from "vitest";
import type { SqlTimeseriesConfigIO } from "~/composables/context/admin/useSqlTimeseriesConfig";
import { useSqlTimeseriesConfig } from "~/composables/context/admin/useSqlTimeseriesConfig";

const ACCESS_TOKEN = "test-admin-token";

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

const defaultConfig: SqlTimeseriesConfigIO = {
  maxRows: 1000000,
  maxDuration: "PT60S",
};

describe("useSqlTimeseriesConfig — refresh()", () => {
  it("starts with isLoading=true on mount", () => {
    mockFetchOk(defaultConfig);
    const { isLoading } = useSqlTimeseriesConfig();
    expect(isLoading.value).toBe(true);
  });

  it("populates config on successful GET", async () => {
    mockFetchOk(defaultConfig);
    const { config, error, refresh } = useSqlTimeseriesConfig();
    await refresh();

    expect(config.value).toEqual(defaultConfig);
    expect(error.value).toBeNull();
  });

  it("sets error message on HTTP failure", async () => {
    mockFetchError(403);
    const { config, error, refresh } = useSqlTimeseriesConfig();
    await refresh();

    expect(error.value).toBe("Failed to load SQL timeseries config");
    expect(config.value).toBeNull();
  });

  it("sends Authorization header with Bearer token", async () => {
    mockFetchOk(defaultConfig);
    const { refresh } = useSqlTimeseriesConfig();
    await refresh();

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/sql-timeseries/config");
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

describe("useSqlTimeseriesConfig — patch()", () => {
  it("sends PATCH with correct body and updates local config", async () => {
    const updated: SqlTimeseriesConfigIO = { maxRows: 500000, maxDuration: "PT2M" };
    mockFetchOk(updated);
    const { config, patch } = useSqlTimeseriesConfig();
    const result = await patch({ maxRows: 500000, maxDuration: "PT2M" });

    expect(result).toEqual(updated);
    expect(config.value).toEqual(updated);

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/sql-timeseries/config");
    expect(opts.method).toBe("PATCH");
    expect(JSON.parse(opts.body as string)).toEqual({
      maxRows: 500000,
      maxDuration: "PT2M",
    });
  });

  it("sends null values to clear fields back to defaults", async () => {
    mockFetchOk(defaultConfig);
    const { patch } = useSqlTimeseriesConfig();
    await patch({ maxRows: null, maxDuration: null });

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(opts.body as string);
    expect(body.maxRows).toBeNull();
    expect(body.maxDuration).toBeNull();
  });

  it("returns null and sets error detail on PATCH 400", async () => {
    mockFetchError(
      400,
      JSON.stringify({ detail: "maxRows must be greater than 0." }),
    );
    const { error, patch } = useSqlTimeseriesConfig();
    const result = await patch({ maxRows: -1 });

    expect(result).toBeNull();
    expect(error.value).toBe("maxRows must be greater than 0.");
  });

  it("returns null and sets error title when detail is absent", async () => {
    mockFetchError(
      400,
      JSON.stringify({ title: "Invalid maxDuration" }),
    );
    const { error, patch } = useSqlTimeseriesConfig();
    const result = await patch({ maxDuration: "INVALID" });

    expect(result).toBeNull();
    expect(error.value).toBe("Invalid maxDuration");
  });

  it("returns null on network error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { error, patch } = useSqlTimeseriesConfig();
    const result = await patch({ maxRows: 100000 });

    expect(result).toBeNull();
    expect(error.value).toBe("Failed to save SQL timeseries config");
  });

  it("resets isSaving to false after a successful PATCH", async () => {
    mockFetchOk(defaultConfig);
    const { isSaving, patch } = useSqlTimeseriesConfig();
    expect(isSaving.value).toBe(false);
    const patchPromise = patch({ maxRows: 100 });
    // isSaving should be true while in flight
    expect(isSaving.value).toBe(true);
    await patchPromise;
    expect(isSaving.value).toBe(false);
  });
});
