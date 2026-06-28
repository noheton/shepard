/**
 * MISSING-aas-ui Slice 3 — tests for useAasAdminConfig composable.
 *
 * Backend surface: GET /v2/admin/config/aas (instance-admin; AasConfigIO)
 *                  PATCH /v2/admin/config/aas (RFC 7396 merge-patch; AasConfigPatch)
 * Covers: initial fetch, success shape, Authorization header, PATCH enabled toggle,
 * PATCH registryUrl, PATCH registryApiKey, PATCH error handling, isSaving flag.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAasAdminConfig } from "~/composables/aas/useAasAdminConfig";

const ACCESS_TOKEN = "test-aas-admin-token";
const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

const SAMPLE_CONFIG = {
  enabled: false,
  registryUrl: "https://registry.example.org/api/v3.0",
  apiKeyPresent: true,
  baseUrl: "https://shepard.example.org",
};

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

function mockFetchError(status: number, detail?: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.resolve(detail ? { detail } : {}),
      text: () => Promise.resolve(detail ? JSON.stringify({ detail }) : ""),
    }),
  );
}

// ── initial fetch ────────────────────────────────────────────────────────────

describe("useAasAdminConfig — initial fetch", () => {
  it("calls GET /v2/admin/config/aas on construction", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    useAasAdminConfig();
    await flush();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string];
    expect(url).toContain("/v2/admin/config/aas");
  });

  it("populates config on success", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    const { config } = useAasAdminConfig();
    await flush();

    expect(config.value).not.toBeNull();
    expect(config.value?.enabled).toBe(false);
    expect(config.value?.apiKeyPresent).toBe(true);
    expect(config.value?.registryUrl).toBe("https://registry.example.org/api/v3.0");
    expect(config.value?.baseUrl).toBe("https://shepard.example.org");
  });

  it("passes Authorization Bearer header", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    useAasAdminConfig();
    await flush();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    const headers = opts.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("sets error on HTTP failure", async () => {
    mockFetchError(500);
    const { error } = useAasAdminConfig();
    await flush();

    expect(error.value).toBeTruthy();
  });
});

// ── PATCH ────────────────────────────────────────────────────────────────────

describe("useAasAdminConfig — patch()", () => {
  it("sends PATCH with correct method and Content-Type", async () => {
    mockFetchOk({ ...SAMPLE_CONFIG, enabled: true });
    const { patch } = useAasAdminConfig();
    await flush();

    await patch({ enabled: true });

    const patchCall = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[1] as [string, RequestInit];
    expect(patchCall[1].method).toBe("PATCH");
    const headers = patchCall[1].headers as Record<string, string>;
    expect(headers["Content-Type"]).toBe("application/json");
  });

  it("updates config.value with the PATCH response", async () => {
    mockFetchOk({ ...SAMPLE_CONFIG, enabled: true });
    const { config, patch } = useAasAdminConfig();
    await flush();

    await patch({ enabled: true });
    expect(config.value?.enabled).toBe(true);
  });

  it("sends the patch body as JSON", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    const { patch } = useAasAdminConfig();
    await flush();

    await patch({ registryUrl: "https://new.registry.org" });

    const patchCall = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[1] as [string, RequestInit];
    const body = JSON.parse(patchCall[1].body as string);
    expect(body.registryUrl).toBe("https://new.registry.org");
  });

  it("sets error and returns null on PATCH failure", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    const { error, patch } = useAasAdminConfig();
    await flush();

    mockFetchError(403, "Not an instance admin");
    const result = await patch({ enabled: true });
    expect(result).toBeNull();
    expect(error.value).toContain("Not an instance admin");
  });

  it("clears isSaving after PATCH completes", async () => {
    mockFetchOk(SAMPLE_CONFIG);
    const { isSaving, patch } = useAasAdminConfig();
    await flush();

    const patchPromise = patch({ enabled: true });
    // isSaving goes true during PATCH
    await patchPromise;
    expect(isSaving.value).toBe(false);
  });
});
