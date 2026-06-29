/**
 * MISSING-aas-ui Slice 2 — tests for useAasShell composable.
 *
 * Backend surface: GET /v2/aas/shells/{appId} (AasShellsRest.java, AAS1b single-Shell)
 *                  GET /v2/aas/shells/{appId}/submodels (AAS1b submodel refs).
 * Covers: init fetch, URL construction, 501 disabled state, 404 not-found state,
 * error state, submodels fetch, Authorization header, and submodelRefToAppId helper.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAasShell, submodelRefToAppId } from "~/composables/aas/useAasShell";

const ACCESS_TOKEN = "test-shell-token";
const COLLECTION_APP_ID = "0197b6a2-7b4c-7000-8a3b-1234567890ab";

const flush = () => new Promise<void>(r => setTimeout(r, 0));

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

const SAMPLE_SHELL = {
  id: `urn:shepard:collection:${COLLECTION_APP_ID}`,
  idShort: "TestCollection",
  assetInformation: { assetKind: "Instance", globalAssetId: `urn:shepard:asset:${COLLECTION_APP_ID}` },
  description: [{ language: "en", text: "A test collection" }],
  submodels: [
    { type: "ExternalReference", keys: [{ type: "Submodel", value: "urn:shepard:dataobject:do-app-id-1" }] },
  ],
};

const SAMPLE_SUBMODELS_PAGE = {
  items: [
    { type: "ExternalReference", keys: [{ type: "Submodel", value: "urn:shepard:dataobject:do-app-id-1" }], displayName: "TR-001 Run" },
    { type: "ExternalReference", keys: [{ type: "Submodel", value: "urn:shepard:dataobject:do-app-id-2" }], displayName: null },
  ],
  total: 2,
  page: 0,
  pageSize: 50,
};

/** Stub fetch to return different responses per URL pattern. */
function mockFetchMulti(responses: Record<string, unknown>) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation((url: string) => {
      for (const [pattern, body] of Object.entries(responses)) {
        if (url.includes(pattern)) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(body),
          });
        }
      }
      return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({}) });
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

// ── init fetch ───────────────────────────────────────────────────────────────

describe("useAasShell — init fetch", () => {
  it("calls GET /v2/aas/shells/{appId} on construction", async () => {
    mockFetchMulti({
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    useAasShell(COLLECTION_APP_ID);
    await flush();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const shellFetchUrl = calls[0]![0] as string;
    expect(shellFetchUrl).toContain(`/v2/aas/shells/${COLLECTION_APP_ID}`);
  });

  it("populates shell on success", async () => {
    mockFetchMulti({
      "/submodels": SAMPLE_SUBMODELS_PAGE,
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    const { shell } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect(shell.value).not.toBeNull();
    expect(shell.value?.idShort).toBe("TestCollection");
  });

  it("passes Authorization Bearer header", async () => {
    mockFetchMulti({
      "/submodels": SAMPLE_SUBMODELS_PAGE,
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    useAasShell(COLLECTION_APP_ID);
    await flush();

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls[0] as [string, RequestInit];
    const headers = opts.headers as Record<string, string>;
    expect(headers["Authorization"]).toBe(`Bearer ${ACCESS_TOKEN}`);
  });

  it("fetches submodels after a successful shell fetch", async () => {
    mockFetchMulti({
      "/submodels": SAMPLE_SUBMODELS_PAGE,
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    const { submodels, submodelsTotal } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect(submodels.value).toHaveLength(2);
    expect(submodelsTotal.value).toBe(2);
  });

  it("submodels URL includes page and pageSize", async () => {
    mockFetchMulti({
      "/submodels": SAMPLE_SUBMODELS_PAGE,
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    useAasShell(COLLECTION_APP_ID);
    await flush();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const submodelUrl = calls.find(([url]) => (url as string).includes("/submodels"))?.[0] as string;
    expect(submodelUrl).toContain("page=0");
    expect(submodelUrl).toContain("pageSize=50");
  });

  it("preserves displayName from submodel references", async () => {
    mockFetchMulti({
      "/submodels": SAMPLE_SUBMODELS_PAGE,
      [`/v2/aas/shells/${COLLECTION_APP_ID}`]: SAMPLE_SHELL,
    });
    const { submodels } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect((submodels.value[0] as { displayName?: string }).displayName).toBe("TR-001 Run");
    expect((submodels.value[1] as { displayName?: string | null }).displayName).toBeNull();
  });
});

// ── disabled state (501) ─────────────────────────────────────────────────────

describe("useAasShell — AAS disabled (501)", () => {
  it("sets isDisabled=true on 501", async () => {
    mockFetchStatus(501);
    const { isDisabled, isNotFound } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect(isDisabled.value).toBe(true);
    expect(isNotFound.value).toBe(false);
  });

  it("does not fetch submodels when AAS is disabled", async () => {
    mockFetchStatus(501);
    useAasShell(COLLECTION_APP_ID);
    await flush();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const submodelCalls = calls.filter(([url]) => (url as string).includes("/submodels"));
    expect(submodelCalls).toHaveLength(0);
  });
});

// ── not-found state (404) ────────────────────────────────────────────────────

describe("useAasShell — Shell not found (404)", () => {
  it("sets isNotFound=true on 404", async () => {
    mockFetchStatus(404);
    const { isNotFound, isDisabled } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect(isNotFound.value).toBe(true);
    expect(isDisabled.value).toBe(false);
  });
});

// ── error state ──────────────────────────────────────────────────────────────

describe("useAasShell — error state", () => {
  it("sets error on unexpected failure", async () => {
    mockFetchStatus(503);
    const { error } = useAasShell(COLLECTION_APP_ID);
    await flush();

    expect(error.value).toBeTruthy();
  });
});

// ── submodelRefToAppId helper ─────────────────────────────────────────────────

describe("submodelRefToAppId", () => {
  it("strips the urn:shepard:dataobject: prefix", () => {
    expect(submodelRefToAppId("urn:shepard:dataobject:do-app-id-1")).toBe("do-app-id-1");
  });

  it("returns the raw value when no URN prefix is present", () => {
    expect(submodelRefToAppId("bare-value")).toBe("bare-value");
  });

  it("handles an empty string without throwing", () => {
    expect(submodelRefToAppId("")).toBe("");
  });
});
