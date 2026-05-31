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

// ─── ADM-USR-GIT-BACKEND-1-FE — list + rotate ─────────────────────────────

describe("useAdminUserGitCredential — listCredentials()", () => {
  it("GETs /v2/admin/users/{username}/git-credentials and populates items", async () => {
    mockFetchOk({
      items: [
        {
          appId: "c1",
          host: "gitlab.dlr.de",
          username: "flo",
          displayName: "DLR GitLab",
          lastRotatedAt: "2026-05-31T12:00:00Z",
        },
        {
          appId: "c2",
          host: "github.com",
          username: "flo",
          displayName: null,
          lastRotatedAt: null,
        },
      ],
    });
    const { listCredentials, items } = useAdminUserGitCredential();
    const list = await listCredentials("flodemo");
    expect(list.length).toBe(2);
    expect(items.value.length).toBe(2);
    expect(items.value[1].lastRotatedAt).toBeNull();
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("/v2/admin/users/flodemo/git-credentials");
  });

  it("URL-encodes the target username in the list call", async () => {
    mockFetchOk({ items: [] });
    const { listCredentials } = useAdminUserGitCredential();
    await listCredentials("user with spaces");
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.at(
      -1,
    ) as [string, RequestInit];
    expect(url).toContain("user%20with%20spaces");
  });

  it("surfaces error message + empty items on 404", async () => {
    mockFetchError(404, JSON.stringify({ title: "User not found" }));
    const { listCredentials, items, error } = useAdminUserGitCredential();
    const list = await listCredentials("noone");
    expect(list).toEqual([]);
    expect(items.value).toEqual([]);
    expect(error.value).toBe("User not found");
  });
});

describe("useAdminUserGitCredential — rotateCredential()", () => {
  it("POSTs newPat to /git-credentials/{appId}/rotate and returns true on 204", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        // rotate response
        .mockResolvedValueOnce({
          ok: true,
          status: 204,
          text: () => Promise.resolve(""),
        })
        // list refresh
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ items: [] }),
          text: () => Promise.resolve("{}"),
        }),
    );
    const { rotateCredential } = useAdminUserGitCredential();
    const ok = await rotateCredential("flo", "cred-1", "newpat");
    expect(ok).toBe(true);
    const fetchSpy = globalThis.fetch as ReturnType<typeof vi.fn>;
    const rotateCall = fetchSpy.mock.calls[0];
    expect(rotateCall[0]).toContain(
      "/v2/admin/users/flo/git-credentials/cred-1/rotate",
    );
    const init = rotateCall[1] as RequestInit;
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body as string)).toEqual({ newPat: "newpat" });
  });

  it("returns false and surfaces error on 400 missing newPat", async () => {
    mockFetchError(400, JSON.stringify({ error: "newPat is required" }));
    const { rotateCredential, error } = useAdminUserGitCredential();
    const ok = await rotateCredential("flo", "cred-1", "");
    expect(ok).toBe(false);
    expect(error.value).toBe("newPat is required");
  });
});
