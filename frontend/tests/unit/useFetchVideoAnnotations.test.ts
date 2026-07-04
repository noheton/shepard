/**
 * VID1b-annotation — unit tests for `useFetchVideoAnnotations`.
 *
 * Covers: URL construction, Bearer auth header, pagination-envelope
 * unwrapping (`page.items ?? page` fallback), error surfacing on non-2xx
 * and network failure, and unauthenticated short-circuit.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFetchVideoAnnotations } from "~/composables/context/useFetchVideoAnnotations";

const ACCESS_TOKEN = "test-video-annot-token";
const REF_APP_ID = "019e6ffc-aaaa-7bcd-9eef-000000000001";
const DO_APP_ID = "019e6ffc-aaaa-7bcd-9eef-000000000002";

const ANNOTATION_1 = {
  appId: "ann-001",
  startSeconds: 1.5,
  endSeconds: 3.0,
  label: "landing-spike",
  description: null,
  aiGenerated: false,
  confidence: null,
};

const ANNOTATION_2 = {
  appId: "ann-002",
  startSeconds: 10.0,
  endSeconds: null,
  label: "point-event",
  description: "Camera shutter",
  aiGenerated: true,
  confidence: 0.92,
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

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
  vi.stubGlobal("handleError", vi.fn());
  vi.stubGlobal("useRuntimeConfig", () => ({
    public: { backendApiUrl: "http://localhost:8080/shepard/api" },
  }));
  // Stub fetch with a no-op default (composable calls refresh() on init).
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ items: [], total: 0, page: 0, pageSize: 0 }) }));
});

// ─── URL construction ────────────────────────────────────────────────────────

describe("useFetchVideoAnnotations — URL", () => {
  it("hits GET /v2/references/{refAppId}/annotations", async () => {
    mockFetchOk({ items: [ANNOTATION_1], total: 1, page: 0, pageSize: 50 });
    const { refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("/v2/references/");
    expect(url).toContain(encodeURIComponent(REF_APP_ID));
    expect(url).toContain("/annotations");
  });

  it("URL-encodes refAppId with special characters", async () => {
    const weirdId = "a/b c";
    mockFetchOk({ items: [], total: 0, page: 0, pageSize: 0 });
    const { refresh } = useFetchVideoAnnotations(DO_APP_ID, weirdId);
    await refresh();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain(encodeURIComponent(weirdId));
    expect(url).not.toContain("a/b c");
  });
});

// ─── Auth header ─────────────────────────────────────────────────────────────

describe("useFetchVideoAnnotations — auth", () => {
  it("sends Bearer Authorization header", async () => {
    mockFetchOk({ items: [], total: 0, page: 0, pageSize: 0 });
    const { refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    const [, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(
      (init.headers as Record<string, string>)["Authorization"],
    ).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("sets fetchError and skips fetch when not authenticated", async () => {
    (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
      data: ref(null),
    });
    vi.stubGlobal("fetch", vi.fn());

    const { fetchError, annotations, refresh } = useFetchVideoAnnotations(
      DO_APP_ID,
      REF_APP_ID,
    );
    // The initial auto-refresh in the composable may have already run;
    // call refresh() explicitly to exercise the no-token path.
    await refresh();

    expect(fetchError.value).toBeTruthy();
    expect(annotations.value).toHaveLength(0);
    // fetch should not have been called after the auth check.
    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.length).toBe(0);
  });
});

// ─── Pagination envelope unwrapping ──────────────────────────────────────────

describe("useFetchVideoAnnotations — pagination envelope", () => {
  it("unwraps PagedResponseIO envelope (page.items)", async () => {
    mockFetchOk({
      items: [ANNOTATION_1, ANNOTATION_2],
      total: 2,
      page: 0,
      pageSize: 50,
    });
    const { annotations, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    expect(annotations.value).toHaveLength(2);
    expect(annotations.value[0]!.label).toBe("landing-spike");
    expect(annotations.value[1]!.appId).toBe("ann-002");
  });

  it("falls back to treating the raw body as an array (no .items key)", async () => {
    mockFetchOk([ANNOTATION_1]);
    const { annotations, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    expect(annotations.value).toHaveLength(1);
    expect(annotations.value[0]!.label).toBe("landing-spike");
  });

  it("handles an empty items array", async () => {
    mockFetchOk({ items: [], total: 0, page: 0, pageSize: 50 });
    const { annotations, fetchError, refresh } = useFetchVideoAnnotations(
      DO_APP_ID,
      REF_APP_ID,
    );
    await refresh();

    expect(annotations.value).toHaveLength(0);
    expect(fetchError.value).toBeNull();
  });
});

// ─── Error handling ───────────────────────────────────────────────────────────

describe("useFetchVideoAnnotations — error handling", () => {
  it("sets fetchError on HTTP 404 (reference not found)", async () => {
    mockFetchError(404, "Not found");
    const { fetchError, annotations, refresh } = useFetchVideoAnnotations(
      DO_APP_ID,
      REF_APP_ID,
    );
    await refresh();

    expect(fetchError.value).toMatch(/404/);
    expect(annotations.value).toHaveLength(0);
  });

  it("sets fetchError on HTTP 403 (forbidden)", async () => {
    mockFetchError(403, '{"title":"Forbidden"}');
    const { fetchError, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    expect(fetchError.value).toMatch(/403/);
  });

  it("sets fetchError on network failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("network down")),
    );
    const { fetchError, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();

    expect(fetchError.value).toContain("network down");
  });

  it("resets fetchError to null on a successful subsequent refresh", async () => {
    mockFetchError(500, "internal");
    const { fetchError, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();
    expect(fetchError.value).toBeTruthy();

    mockFetchOk({ items: [], total: 0, page: 0, pageSize: 0 });
    await refresh();
    expect(fetchError.value).toBeNull();
  });
});

// ─── Loading state ────────────────────────────────────────────────────────────

describe("useFetchVideoAnnotations — loading state", () => {
  it("isLoading resets to false after a successful refresh", async () => {
    mockFetchOk({ items: [ANNOTATION_1], total: 1, page: 0, pageSize: 50 });
    const { isLoading, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();
    expect(isLoading.value).toBe(false);
  });

  it("isLoading resets to false after an error", async () => {
    mockFetchError(500, "boom");
    const { isLoading, refresh } = useFetchVideoAnnotations(DO_APP_ID, REF_APP_ID);
    await refresh();
    expect(isLoading.value).toBe(false);
  });
});
