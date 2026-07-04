/**
 * Tests for ADM-USR-ORCID composable + the validator integration.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAdminUserOrcid } from "~/composables/context/admin/useAdminUserOrcid";
import { isValidOrcid } from "~/utils/orcidFormat";

const ACCESS_TOKEN = "test-admin-token";

beforeEach(() => {
  vi.clearAllMocks();
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    data: ref<{ accessToken: string }>({ accessToken: ACCESS_TOKEN }),
  });
});

function mockFetchOk() {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({}),
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

describe("useAdminUserOrcid — patchOrcid()", () => {
  it("PATCHes the /v2/admin/users/{username}/orcid endpoint", async () => {
    mockFetchOk();
    const { patchOrcid } = useAdminUserOrcid();
    const ok = await patchOrcid("alice", "0000-0001-6033-801X");
    expect(ok).toBe(true);

    const [url, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("/v2/admin/users/alice/orcid");
    expect(opts.method).toBe("PATCH");
    expect(JSON.parse(opts.body as string)).toEqual({
      orcid: "0000-0001-6033-801X",
    });
    expect((opts.headers as Record<string, string>)["Authorization"]).toBe(
      `Bearer ${ACCESS_TOKEN}`,
    );
  });

  it("passes null to clear the ORCID", async () => {
    mockFetchOk();
    const { patchOrcid } = useAdminUserOrcid();
    const ok = await patchOrcid("alice", null);
    expect(ok).toBe(true);

    const [, opts] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(JSON.parse(opts.body as string)).toEqual({ orcid: null });
  });

  it("URL-encodes the target username", async () => {
    mockFetchOk();
    const { patchOrcid } = useAdminUserOrcid();
    await patchOrcid("user with spaces", null);
    const [url] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock
      .calls.at(-1) as [string, RequestInit];
    expect(url).toContain("user%20with%20spaces");
  });

  it("returns false and surfaces detail on 400", async () => {
    mockFetchError(
      400,
      JSON.stringify({ error: "Invalid ORCID format." }),
    );
    const { patchOrcid, error } = useAdminUserOrcid();
    const ok = await patchOrcid("alice", "XXXX-YYYY-ZZZZ-WWWW");
    expect(ok).toBe(false);
    expect(error.value).toBe("Invalid ORCID format.");
  });

  it("returns false on 404 user-not-found", async () => {
    mockFetchError(404, "");
    const { patchOrcid, error } = useAdminUserOrcid();
    const ok = await patchOrcid("ghost", "0000-0001-6033-801X");
    expect(ok).toBe(false);
    expect(error.value).toContain("404");
  });
});

describe("ORCID validator — admin pane reuses isValidOrcid", () => {
  it("accepts the canonical 16-digit form with mod 11-2 checksum", () => {
    expect(isValidOrcid("0000-0001-6033-801X")).toBe(true);
  });

  it("rejects bad checksums", () => {
    expect(isValidOrcid("0000-0001-6033-8010")).toBe(false);
  });

  it("rejects malformed input", () => {
    expect(isValidOrcid("not-an-orcid")).toBe(false);
    expect(isValidOrcid("")).toBe(false);
    expect(isValidOrcid(null)).toBe(false);
  });
});
