/**
 * MISSING-aas-ui Slice 1 — tests for useAasShells composable.
 *
 * Backend surface: GET /v2/aas/shells (AasShellsRest.java, AAS1a).
 * Covers: init fetch, default pagination params, 501 disabled state,
 * error state, Authorization header, and the shellIdToAppId helper.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAasShells, shellIdToAppId } from "~/composables/aas/useAasShells";

const ACCESS_TOKEN = "test-aas-token";

/** Flush the microtask queue so the init async refresh() settles. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

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
    }),
  );
}

function mockFetchStatus(status: number) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve({}),
    }),
  );
}

const SAMPLE_SHELL = {
  id: "urn:shepard:collection:0197b6a2-7b4c-7000-8a3b-1234567890ab",
  idShort: "TestCollection",
  assetInformation: { assetKind: "Instance", globalAssetId: "global-asset-1" },
  description: [{ language: "en", text: "A test collection" }],
  submodels: [],
};

function pageOf(items: unknown[], total = items.length) {
  return { items, total, page: 0, pageSize: 50 };
}

// ── init fetch ──────────────────────────────────────────────────────────────

describe("useAasShells — init fetch", () => {
  it("calls GET /v2/aas/shells on construction", async () => {
    mockFetchOk(pageOf([]));
    useAasShells();
    await flush();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls[0] as [string, RequestInit];
    expect(url).toContain("/v2/aas/shells");
  });

  it("includes page=0 and pageSize=50 by default", async () => {
    mockFetchOk(pageOf([]));
    useAasShells();
    await flush();

    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls[0] as [string, RequestInit];
    expect(url).toContain("page=0");
    expect(url).toContain("pageSize=50");
  });

  it("populates shells and total on success", async () => {
    mockFetchOk(pageOf([SAMPLE_SHELL], 7));
    const { shells, total } = useAasShells();
    await flush();

    expect(shells.value).toHaveLength(1);
    expect(shells.value[0]?.idShort).toBe("TestCollection");
    expect(total.value).toBe(7);
  });

  it("passes Authorization Bearer header", async () => {
    mockFetchOk(pageOf([]));
    useAasShells();
    await flush();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls[0] as [string, RequestInit];
    const headers = opts.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });
});

// ── disabled state (501) ────────────────────────────────────────────────────

describe("useAasShells — AAS disabled (501)", () => {
  it("sets isDisabled=true when backend returns 501", async () => {
    mockFetchStatus(501);
    const { isDisabled, shells, total } = useAasShells();
    await flush();

    expect(isDisabled.value).toBe(true);
    expect(shells.value).toHaveLength(0);
    expect(total.value).toBe(0);
  });

  it("does not set error on 501 (disabled is not an error state)", async () => {
    mockFetchStatus(501);
    const { error } = useAasShells();
    await flush();

    expect(error.value).toBeNull();
  });
});

// ── error state ─────────────────────────────────────────────────────────────

describe("useAasShells — error state", () => {
  it("sets error on non-501 failure and leaves isDisabled false", async () => {
    mockFetchStatus(503);
    const { error, isDisabled } = useAasShells();
    await flush();

    expect(error.value).toBeTruthy();
    expect(isDisabled.value).toBe(false);
  });
});

// ── shellIdToAppId helper ───────────────────────────────────────────────────

describe("shellIdToAppId", () => {
  it("strips the urn:shepard:collection: prefix", () => {
    expect(
      shellIdToAppId(
        "urn:shepard:collection:0197b6a2-7b4c-7000-8a3b-1234567890ab",
      ),
    ).toBe("0197b6a2-7b4c-7000-8a3b-1234567890ab");
  });

  it("returns the raw value when no URN prefix is present", () => {
    expect(shellIdToAppId("bare-app-id")).toBe("bare-app-id");
  });

  it("handles an empty string without throwing", () => {
    expect(shellIdToAppId("")).toBe("");
  });
});
