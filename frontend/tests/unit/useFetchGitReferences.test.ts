/**
 * BUG-DO-DETAIL-A-TOAST-2026-06-29 — unit tests for useFetchGitReferences.
 *
 * The composable now bypasses the generated `ReferencesApi` client (whose
 * `listReferencesRaw` does `jsonValue.map(...)` and breaks on a paged
 * envelope) and uses a raw `fetch` + envelope unwrap, matching the other
 * v2 reference composables.
 *
 * Verifies that:
 *  - the composable hits GET /v2/references?kind=git&dataObjectAppId=...
 *  - the paged envelope `{ items: [...] }` is unwrapped (regression for
 *    the "Error while listGitReferences: r.map is not a function" toast
 *    observed on the DataObject detail page).
 *  - plain-array responses still work (back-compat with the pre-envelope
 *    backend shape).
 *  - payload fields are mapped correctly to GitReferenceIO.
 *  - 404 / 400 → empty list (parity with HDF / video / spatial).
 *  - other API errors leave gitReferences empty and clear isLoading.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: vi.fn().mockResolvedValue(undefined),
  data: ref<{ accessToken: string } | null>({ accessToken: "test-token" }),
  signIn: vi.fn().mockResolvedValue(undefined),
});

beforeEach(() => {
  vi.clearAllMocks();
});

const RAW_GIT_REF = {
  appId: "git-ref-001",
  id: 10,
  name: "My Repo Ref",
  kind: "git",
  payload: {
    repoUrl: "https://github.com/dlr/shepard",
    ref: "main",
    path: "data/sensor_logs",
    mode: "READ",
  },
};

const RAW_GIT_REF_MINIMAL = {
  appId: "git-ref-002",
  kind: "git",
  payload: {
    repoUrl: "https://gitlab.com/example/repo",
  },
};

function okResponse(body: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(""),
  } as Response);
}

function statusResponse(status: number, ok = false) {
  return Promise.resolve({
    ok,
    status,
    json: () => Promise.reject(new Error("not json")),
    text: () => Promise.resolve(`HTTP ${status}`),
  } as unknown as Response);
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchGitReferences", () => {
  it("calls /v2/references?kind=git with the dataObjectAppId", async () => {
    mockFetch.mockReturnValue(okResponse({ items: [RAW_GIT_REF] }));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    useFetchGitReferences("do-app-001");
    await flush();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/v2/references?kind=git&dataObjectAppId=do-app-001"),
      expect.objectContaining({ headers: expect.objectContaining({ Accept: "application/json" }) }),
    );
  });

  it("BUG-DO-DETAIL-A-TOAST-2026-06-29 — unwraps the paged envelope { items: [...] }", async () => {
    mockFetch.mockReturnValue(okResponse({ items: [RAW_GIT_REF], totalElements: 1 }));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toHaveLength(1);
    expect(gitReferences.value[0]!.appId).toBe("git-ref-001");
  });

  it("still accepts a plain-array response (back-compat)", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_GIT_REF]));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toHaveLength(1);
    expect(gitReferences.value[0]!.appId).toBe("git-ref-001");
  });

  it("maps payload fields to GitReferenceIO correctly", async () => {
    mockFetch.mockReturnValue(okResponse({ items: [RAW_GIT_REF] }));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toHaveLength(1);
    const ref = gitReferences.value[0]!;
    expect(ref.appId).toBe("git-ref-001");
    expect(ref.repoUrl).toBe("https://github.com/dlr/shepard");
    expect(ref.ref).toBe("main");
    expect(ref.path).toBe("data/sensor_logs");
  });

  it("handles a minimal payload (only repoUrl required)", async () => {
    mockFetch.mockReturnValue(okResponse({ items: [RAW_GIT_REF_MINIMAL] }));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toHaveLength(1);
    const ref = gitReferences.value[0]!;
    expect(ref.appId).toBe("git-ref-002");
    expect(ref.repoUrl).toBe("https://gitlab.com/example/repo");
    expect(ref.ref).toBeUndefined();
    expect(ref.path).toBeUndefined();
  });

  it("treats 404 / 400 as empty list (plugin disabled / DO missing)", async () => {
    mockFetch.mockReturnValue(statusResponse(404));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences, isLoading } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });

  it("leaves gitReferences empty and clears isLoading on API error", async () => {
    mockFetch.mockReturnValue(statusResponse(500));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences, isLoading } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });
});
