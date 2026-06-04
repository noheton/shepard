/**
 * PLUGIN-REF-HANDLER-FE-REPOINT — unit tests for the repointed
 * useFetchGitReferences composable.
 *
 * Verifies that:
 *  - the composable calls GET /v2/references?kind=git&dataObjectAppId=...
 *  - payload fields are mapped correctly to GitReferenceIO
 *  - API errors leave gitReferences empty and clear isLoading
 *  - return type still satisfies the GitReferenceIO shape expected by consumers
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// Override the default setup.ts useAuth stub to provide a valid access token.
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
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "alice",
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

function errorResponse(status: number) {
  return Promise.resolve({
    ok: false,
    status,
    json: () => Promise.reject(new Error("not json")),
    text: () => Promise.resolve(`HTTP ${status}`),
  } as unknown as Response);
}

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchGitReferences", () => {
  it("calls the unified /v2/references?kind=git endpoint", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_GIT_REF]));

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

  it("maps payload fields to GitReferenceIO correctly", async () => {
    mockFetch.mockReturnValue(okResponse([RAW_GIT_REF]));

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
    mockFetch.mockReturnValue(okResponse([RAW_GIT_REF_MINIMAL]));

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

  it("leaves gitReferences empty and clears isLoading on API error", async () => {
    mockFetch.mockReturnValue(errorResponse(500));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences, isLoading } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });
});
