import { describe, it, expect, vi, beforeEach } from "vitest";
import type { JupyterConfigIO } from "~/composables/context/admin/useJupyterConfig";
import { useJupyterConfig } from "~/composables/context/admin/useJupyterConfig";

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

const enabledConfig: JupyterConfigIO = {
  enabled: true,
  hubUrl: "https://hub.example.org",
};

const disabledConfig: JupyterConfigIO = {
  enabled: false,
  hubUrl: null,
};

describe("useJupyterConfig — refresh()", () => {
  it("starts with isLoading=true on mount", () => {
    mockFetchOk(enabledConfig);
    const { isLoading } = useJupyterConfig();
    expect(isLoading.value).toBe(true);
  });

  it("populates config on successful GET", async () => {
    mockFetchOk(enabledConfig);
    const { config, error, refresh } = useJupyterConfig();
    await refresh();

    expect(config.value).toEqual(enabledConfig);
    expect(error.value).toBeNull();
  });

  it("uses the PUBLIC endpoint by default (non-admin)", async () => {
    mockFetchOk(disabledConfig);
    const { refresh } = useJupyterConfig();
    await refresh();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/config/jupyter");
    expect(url).not.toContain("/v2/admin/config/jupyter");
  });

  it("uses the ADMIN endpoint when adminMode is true", async () => {
    mockFetchOk(enabledConfig);
    const { refresh } = useJupyterConfig({ adminMode: true });
    await refresh();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/config/jupyter");
  });

  it("sets error message on HTTP failure (adminMode only — public mode is fail-soft)", async () => {
    mockFetchError(403);
    const { config, error, refresh } = useJupyterConfig({ adminMode: true });
    await refresh();

    expect(error.value).toBe("Failed to load Jupyter config");
    expect(config.value).toBeNull();
  });

  it("sends Authorization header with Bearer token", async () => {
    mockFetchOk(enabledConfig);
    const { refresh } = useJupyterConfig();
    await refresh();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });
});

describe("useJupyterConfig — patch()", () => {
  it("ALWAYS targets the ADMIN endpoint, even when read mode is public", async () => {
    mockFetchOk(enabledConfig);
    const { patch } = useJupyterConfig(); // public read mode by default
    await patch({ enabled: true });

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/config/jupyter");
    expect(opts.method).toBe("PATCH");
  });

  it("uses application/merge-patch+json content type", async () => {
    mockFetchOk(enabledConfig);
    const { patch } = useJupyterConfig({ adminMode: true });
    await patch({ enabled: true });

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect((opts.headers as Record<string, string>)["Content-Type"]).toBe(
      "application/merge-patch+json",
    );
  });

  it("sends PATCH with correct body and updates local config", async () => {
    const updated: JupyterConfigIO = {
      enabled: true,
      hubUrl: "https://new.example.org",
    };
    mockFetchOk(updated);
    const { config, patch } = useJupyterConfig({ adminMode: true });
    const result = await patch({ enabled: true, hubUrl: "https://new.example.org" });

    expect(result).toEqual(updated);
    expect(config.value).toEqual(updated);
    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(JSON.parse(opts.body as string)).toEqual({
      enabled: true,
      hubUrl: "https://new.example.org",
    });
  });

  it("sends null hubUrl to clear it back to deploy-time default", async () => {
    mockFetchOk(disabledConfig);
    const { patch } = useJupyterConfig({ adminMode: true });
    await patch({ hubUrl: null });

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(opts.body as string);
    expect(body.hubUrl).toBeNull();
  });

  it("returns null and sets error detail on PATCH 400", async () => {
    mockFetchError(
      400,
      JSON.stringify({ detail: "hubUrl must be a valid absolute http(s) URL." }),
    );
    const { error, patch } = useJupyterConfig({ adminMode: true });
    const result = await patch({ hubUrl: "not-a-url" });

    expect(result).toBeNull();
    expect(error.value).toBe("hubUrl must be a valid absolute http(s) URL.");
  });

  it("returns null on network error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("Network down")),
    );
    const { error, patch } = useJupyterConfig({ adminMode: true });
    const result = await patch({ enabled: false });

    expect(result).toBeNull();
    expect(error.value).toBe("Failed to save Jupyter config");
  });
});
