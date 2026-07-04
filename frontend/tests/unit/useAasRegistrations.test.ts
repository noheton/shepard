/**
 * MISSING-aas-ui Slice 6 — tests for useAasRegistrations composable.
 *
 * Backend surface:
 *   GET  /v2/admin/aas/registrations      — paged AasRegistrationIO list
 *   POST /v2/admin/aas/registrations/sync — AasSyncResultIO
 *
 * Covers: initial load URL, success shape, Authorization header, pagination params,
 * triggerSync URL + method, lastSyncResult populated, reload on sync, error states.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  useAasRegistrations,
  type AasRegistrationIO,
  type AasRegistrationsPage,
} from "~/composables/aas/useAasRegistrations";

const ACCESS_TOKEN = "test-aas-registrations-token";
const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

const SAMPLE_ROW: AasRegistrationIO = {
  appId: "reg-app-id-1",
  shellAppId: "coll-app-id-1",
  registryUrl: "https://registry.example.org/api/v3.0",
  status: "SYNCED",
  lastAttemptAt: 1719500000000,
  errorMessage: null,
  createdAt: 1719400000000,
  updatedAt: 1719500000000,
};

const SAMPLE_PAGE: AasRegistrationsPage = {
  items: [SAMPLE_ROW],
  total: 1,
  page: 0,
  pageSize: 50,
};

function mockFetchOk(body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(body),
    }),
  );
}

function mockFetchError(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      json: () => Promise.resolve({}),
    }),
  );
}

// ── initial load ─────────────────────────────────────────────────────────────

describe("useAasRegistrations — initial load", () => {
  it("calls GET /v2/admin/aas/registrations on construction", async () => {
    mockFetchOk(SAMPLE_PAGE);
    useAasRegistrations();
    await flush();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string];
    expect(url).toContain("/v2/admin/aas/registrations");
  });

  it("populates registrationsPage on success", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { registrationsPage } = useAasRegistrations();
    await flush();

    expect(registrationsPage.value).not.toBeNull();
    expect(registrationsPage.value?.total).toBe(1);
    expect(registrationsPage.value?.items[0]?.status).toBe("SYNCED");
    expect(registrationsPage.value?.items[0]?.shellAppId).toBe("coll-app-id-1");
  });

  it("passes Authorization Bearer header", async () => {
    mockFetchOk(SAMPLE_PAGE);
    useAasRegistrations();
    await flush();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
    const headers = opts.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("includes page and pageSize query params", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { load } = useAasRegistrations();
    await flush();

    await load(2, 25);
    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const [lastUrl] = calls[calls.length - 1] as [string];
    expect(lastUrl).toContain("page=2");
    expect(lastUrl).toContain("pageSize=25");
  });

  it("sets error on HTTP failure", async () => {
    mockFetchError(403);
    const { error } = useAasRegistrations();
    await flush();

    expect(error.value).toBeTruthy();
  });
});

// ── triggerSync ───────────────────────────────────────────────────────────────

describe("useAasRegistrations — triggerSync()", () => {
  it("sends POST to /v2/admin/aas/registrations/sync", async () => {
    mockFetchOk({ synced: 3 });
    const { triggerSync } = useAasRegistrations();
    await flush();

    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ synced: 3 }) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(SAMPLE_PAGE) }),
    );

    await triggerSync();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const syncCall = calls[0] as [string, RequestInit];
    expect(syncCall[0]).toContain("/v2/admin/aas/registrations/sync");
    expect(syncCall[1].method).toBe("POST");
  });

  it("sets lastSyncResult on success", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { triggerSync, lastSyncResult } = useAasRegistrations();
    await flush();

    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ synced: 5 }) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(SAMPLE_PAGE) }),
    );

    await triggerSync();
    expect(lastSyncResult.value?.synced).toBe(5);
  });

  it("reloads registrations after successful sync", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { triggerSync } = useAasRegistrations();
    await flush();

    // Replace stub with fresh mock so call count starts at 0.
    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ synced: 1 }) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(SAMPLE_PAGE) }),
    );

    await triggerSync();
    // The new mock should have been called twice: POST sync + GET reload.
    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.length).toBe(2);
  });

  it("sets error on sync failure", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { triggerSync, error } = useAasRegistrations();
    await flush();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 502, json: () => Promise.resolve({}) }));
    const result = await triggerSync();
    expect(result).toBeNull();
    expect(error.value).toBeTruthy();
  });

  it("clears isSyncing after sync completes", async () => {
    mockFetchOk(SAMPLE_PAGE);
    const { triggerSync, isSyncing } = useAasRegistrations();
    await flush();

    vi.stubGlobal(
      "fetch",
      vi.fn()
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve({ synced: 0 }) })
        .mockResolvedValueOnce({ ok: true, status: 200, json: () => Promise.resolve(SAMPLE_PAGE) }),
    );

    await triggerSync();
    expect(isSyncing.value).toBe(false);
  });
});
