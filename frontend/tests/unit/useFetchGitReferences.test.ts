/**
 * PLUGIN-REF-HANDLER-FE-REPOINT — unit tests for the repointed
 * useFetchGitReferences composable.
 *
 * V2-SWEEP-001-CLIENT-REGEN: the composable now calls the typed
 * `ReferencesApi.listReferences({ kind: "git", dataObjectAppId })` through
 * `useV2ShepardApi` (was a raw `fetch` shim). The test mocks the v2 helper.
 *
 * Verifies that:
 *  - the composable calls listReferences with kind=git + the dataObjectAppId
 *  - payload fields are mapped correctly to GitReferenceIO
 *  - API errors leave gitReferences empty and clear isLoading
 *  - return type still satisfies the GitReferenceIO shape expected by consumers
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListReferences = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listReferences: mockListReferences }),
  );
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

const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useFetchGitReferences", () => {
  it("calls listReferences with kind=git and the dataObjectAppId", async () => {
    mockListReferences.mockResolvedValue([RAW_GIT_REF]);

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    useFetchGitReferences("do-app-001");
    await flush();

    expect(mockListReferences).toHaveBeenCalledWith({
      kind: "git",
      dataObjectAppId: "do-app-001",
    });
  });

  it("maps payload fields to GitReferenceIO correctly", async () => {
    mockListReferences.mockResolvedValue([RAW_GIT_REF]);

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
    mockListReferences.mockResolvedValue([RAW_GIT_REF_MINIMAL]);

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
    mockListReferences.mockRejectedValue(new Error("500"));

    const { useFetchGitReferences } = await import(
      "~/composables/context/useFetchGitReferences"
    );
    const { gitReferences, isLoading } = useFetchGitReferences("do-app-001");
    await flush();

    expect(gitReferences.value).toEqual([]);
    expect(isLoading.value).toBe(false);
  });
});
