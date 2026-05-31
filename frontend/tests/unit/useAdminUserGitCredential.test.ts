/**
 * Tests for ADM-USR-GIT composable.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAdminUserGitCredential } from "~/composables/context/admin/useAdminUserGitCredential";

const ACCESS_TOKEN = "test-admin-token";

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
      json: () => Promise.resolve(body),
      text: () => Promise.resolve(""),
    }),
  );
}

function mockFetchError(status: number, bodyText = "") {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status,
      text: () => Promise.resolve(bodyText),
    }),
  );
}

describe("useAdminUserGitCredential — setCredential()", () => {
  it("POSTs to /v2/admin/users/{username}/git-credentials with the full body", async () => {
    mockFetchOk({ appId: "abc123", host: "gitlab.dlr.de", username: "flo" });
    const { setCredential } = useAdminUserGitCredential();
    const result = await setCredential("flodemo", {
      host: "gitlab.dlr.de",
      username: "flo",
      pat: "glpat-secret",
      displayName: "DLR GitLab",
    });
    expect(result?.appId).toBe("abc123");

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/users/flodemo/git-credentials");
    expect(opts.method).toBe("POST");
    const body = JSON.parse(opts.body as string);
    expect(body).toEqual({
      host: "gitlab.dlr.de",
      username: "flo",
      pat: "glpat-secret",
      displayName: "DLR GitLab",
    });
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("omits displayName when undefined", async () => {
    mockFetchOk({ appId: "abc", host: "github.com", username: "flo" });
    const { setCredential } = useAdminUserGitCredential();
    await setCredential("flo", {
      host: "github.com",
      username: "flo",
      pat: "secret",
    });
    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    const body = JSON.parse(opts.body as string);
    expect(body.displayName).toBeUndefined();
  });

  it("URL-encodes the target username", async () => {
    mockFetchOk({ appId: "abc", host: "gitlab.dlr.de", username: "flo" });
    const { setCredential } = useAdminUserGitCredential();
    await setCredential("user with spaces", {
      host: "gitlab.dlr.de",
      username: "flo",
      pat: "p",
    });
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("user%20with%20spaces");
  });

  it("returns null and surfaces detail on 400 missing field", async () => {
    mockFetchError(400, JSON.stringify({ error: "host is required" }));
    const { setCredential, error } = useAdminUserGitCredential();
    const result = await setCredential("flo", {
      host: "",
      username: "flo",
      pat: "x",
    });
    expect(result).toBeNull();
    expect(error.value).toBe("host is required");
  });

  it("returns null on 503 missing encryption key", async () => {
    mockFetchError(
      503,
      JSON.stringify({
        error:
          "shepard.secrets.encryption-key is not configured — git credentials cannot be stored",
      }),
    );
    const { setCredential, error } = useAdminUserGitCredential();
    const result = await setCredential("flo", {
      host: "gitlab.dlr.de",
      username: "flo",
      pat: "x",
    });
    expect(result).toBeNull();
    expect(error.value).toContain("encryption-key");
  });

  it("retains the last successful result", async () => {
    mockFetchOk({ appId: "xyz", host: "gitlab.dlr.de", username: "flo" });
    const { setCredential, lastResult } = useAdminUserGitCredential();
    await setCredential("flo", {
      host: "gitlab.dlr.de",
      username: "flo",
      pat: "x",
    });
    expect(lastResult.value).toEqual({
      appId: "xyz",
      host: "gitlab.dlr.de",
      username: "flo",
    });
  });
});
