/**
 * SCENEGRAPH-CANVAS-1 — unit tests for useUrdfReferenceBlob.
 *
 * Mirrors the fetch-mocked plumbing in useScenegraphFromUrdf.test.ts. The
 * object-URL materialisation is exercised via a stubbed URL.createObjectURL
 * so the test runs headless (jsdom has no real blob-URL support for
 * urdf-loader, which is mounted only in Playwright).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  v2FileContentUrl,
  urdfBlobErrorForStatus,
  useUrdfReferenceBlob,
} from "../../composables/useUrdfReferenceBlob";

interface FakeAuthData {
  accessToken: string;
}

function mockAuth(accessToken: string | null): void {
  (globalThis as unknown as { useAuth: () => unknown }).useAuth = () => ({
    refresh: vi.fn().mockResolvedValue(undefined),
    data: ref<FakeAuthData | null>(
      accessToken === null ? null : { accessToken },
    ),
    signIn: vi.fn().mockResolvedValue(undefined),
  });
}

function mockRuntimeConfig(backendApiUrl: string): void {
  (globalThis as unknown as { useRuntimeConfig: () => unknown }).useRuntimeConfig =
    () => ({ public: { backendApiUrl } });
}

// ── v2FileContentUrl (pure) ──────────────────────────────────────────────────

describe("v2FileContentUrl", () => {
  const appId = "0197b6a2-aaaa-7000-8000-000000000099";

  it("strips a trailing /shepard/api carrier base", () => {
    expect(
      v2FileContentUrl("http://localhost:8080/shepard/api", appId),
    ).toBe(`http://localhost:8080/v2/references/${appId}/content`);
  });

  it("handles a bare host with no carrier suffix", () => {
    expect(v2FileContentUrl("http://localhost:8080", appId)).toBe(
      `http://localhost:8080/v2/references/${appId}/content`,
    );
  });

  it("strips a trailing slash", () => {
    expect(v2FileContentUrl("http://localhost:8080/", appId)).toBe(
      `http://localhost:8080/v2/references/${appId}/content`,
    );
  });

  it("URL-encodes a hostile appId", () => {
    expect(v2FileContentUrl("http://x", "bad/id")).toBe(
      "http://x/v2/references/bad%2Fid/content",
    );
  });
});

// ── urdfBlobErrorForStatus (pure) ────────────────────────────────────────────

describe("urdfBlobErrorForStatus", () => {
  it("maps the documented statuses", () => {
    expect(urdfBlobErrorForStatus(401)).toMatch(/expired/i);
    expect(urdfBlobErrorForStatus(403)).toMatch(/access/i);
    expect(urdfBlobErrorForStatus(404)).toMatch(/no longer exists/i);
    expect(urdfBlobErrorForStatus(0)).toMatch(/network/i);
  });

  it("falls back to a generic HTTP-coded message", () => {
    expect(urdfBlobErrorForStatus(500)).toMatch(/HTTP 500/);
  });
});

// ── resolve (wire I/O, mocked fetch + URL) ───────────────────────────────────

describe("useUrdfReferenceBlob — resolve", () => {
  const originalFetch = globalThis.fetch;
  const appId = "0197b6a2-aaaa-7000-8000-000000000099";

  beforeEach(() => {
    mockAuth("test-token");
    mockRuntimeConfig("http://localhost:8080/shepard/api");
    globalThis.URL.createObjectURL = vi.fn(() => "blob:fake-url");
    globalThis.URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  it("GETs the content endpoint with the bearer token and returns a blob URL", async () => {
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response("<robot/>", { status: 200 }),
    );
    globalThis.fetch = fetchSpy as unknown as typeof fetch;

    const { resolve, objectUrl } = useUrdfReferenceBlob();
    const url = await resolve(appId);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [hitUrl, init] = fetchSpy.mock.calls[0]!;
    expect(hitUrl).toBe(`http://localhost:8080/v2/references/${appId}/content`);
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer test-token");
    expect(url).toBe("blob:fake-url");
    expect(objectUrl.value).toBe("blob:fake-url");
  });

  it("sets a 404 error and returns null when the file is gone", async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(new Response("", { status: 404 })) as unknown as typeof fetch;

    const { resolve, error, objectUrl } = useUrdfReferenceBlob();
    const url = await resolve(appId);

    expect(url).toBeNull();
    expect(objectUrl.value).toBeNull();
    expect(error.value?.status).toBe(404);
    expect(error.value?.message).toMatch(/no longer exists/i);
  });

  it("returns a 401 error when no access token is present", async () => {
    mockAuth(null);
    const { resolve, error } = useUrdfReferenceBlob();
    const url = await resolve(appId);
    expect(url).toBeNull();
    expect(error.value?.status).toBe(401);
  });

  it("surfaces a network error as status 0", async () => {
    globalThis.fetch = vi
      .fn()
      .mockRejectedValue(new Error("ECONNREFUSED")) as unknown as typeof fetch;

    const { resolve, error } = useUrdfReferenceBlob();
    const url = await resolve(appId);
    expect(url).toBeNull();
    expect(error.value?.status).toBe(0);
  });

  it("revokes the previous object URL when re-resolving", async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(new Response("<robot/>", { status: 200 })) as unknown as typeof fetch;

    const { resolve, revoke } = useUrdfReferenceBlob();
    await resolve(appId);
    await resolve(appId); // second resolve must revoke the first URL
    expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith("blob:fake-url");
    revoke();
  });
});
