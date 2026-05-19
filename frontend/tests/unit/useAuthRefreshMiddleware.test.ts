import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ResponseContext } from "@dlr-shepard/backend-client";

// Override the global useAuth / useRouter stubs before the module is imported
// so each test suite can install per-test mocks.
const mockRefresh = vi.fn();
const mockSignIn = vi.fn();
const mockDataRef = ref<{ accessToken: string } | null>(null);
const mockCurrentRoute = ref({ fullPath: "/test" });

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: mockRefresh,
  data: mockDataRef,
  signIn: mockSignIn,
});
(globalThis as unknown as Record<string, unknown>).useRouter = () => ({
  currentRoute: mockCurrentRoute,
});

import { useAuthRefreshMiddleware } from "~/composables/common/api/useAuthRefreshMiddleware";

function makeContext(
  status: number,
  fetchFn: ReturnType<typeof vi.fn> = vi.fn(),
  authHeader = "Bearer old-token",
): ResponseContext {
  return {
    response: { status } as Response,
    fetch: fetchFn,
    url: "https://api.example.com/resource",
    init: { headers: { Authorization: authHeader } },
  } as unknown as ResponseContext;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockRefresh.mockResolvedValue(undefined);
  mockSignIn.mockResolvedValue(undefined);
  mockDataRef.value = { accessToken: "refreshed-token" };
  mockCurrentRoute.value = { fullPath: "/test" };
});

describe("useAuthRefreshMiddleware", () => {
  it("returns a Middleware object with a post hook", () => {
    const m = useAuthRefreshMiddleware();
    expect(m).toHaveProperty("post");
    expect(typeof m.post).toBe("function");
  });

  it("ignores non-401 responses (200)", async () => {
    const m = useAuthRefreshMiddleware();
    const result = await m.post!(makeContext(200));
    expect(result).toBeUndefined();
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("ignores non-401 responses (403)", async () => {
    const m = useAuthRefreshMiddleware();
    const result = await m.post!(makeContext(403));
    expect(result).toBeUndefined();
    expect(mockRefresh).not.toHaveBeenCalled();
  });

  it("calls refresh() on a 401 and retries with the new token", async () => {
    const retryResponse = new Response(null, { status: 200 });
    const fetchFn = vi.fn().mockResolvedValue(retryResponse);

    const m = useAuthRefreshMiddleware();
    const result = await m.post!(makeContext(401, fetchFn));

    expect(mockRefresh).toHaveBeenCalledOnce();
    expect(fetchFn).toHaveBeenCalledOnce();
    const usedHeaders = (fetchFn.mock.calls[0] as [string, { headers: { Authorization: string } }])[1].headers;
    expect(usedHeaders.Authorization).toBe("Bearer refreshed-token");
    expect(result).toBe(retryResponse);
  });

  it("redirects to signIn when refresh() throws", async () => {
    mockRefresh.mockRejectedValue(new Error("refresh_token expired"));

    const m = useAuthRefreshMiddleware();
    await m.post!(makeContext(401));

    expect(mockSignIn).toHaveBeenCalledWith("oidc", expect.objectContaining({ redirect: true }));
    const [, opts] = mockSignIn.mock.calls[0] as [string, { callbackUrl: string }];
    expect(opts.callbackUrl).toContain(encodeURIComponent("/test"));
  });

  it("redirects to signIn when retry also returns 401", async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));

    const m = useAuthRefreshMiddleware();
    await m.post!(makeContext(401, fetchFn));

    expect(mockSignIn).toHaveBeenCalledWith("oidc", expect.objectContaining({ redirect: true }));
  });

  it("redirects to signIn when accessToken is null after refresh", async () => {
    mockDataRef.value = null; // no token available after refresh

    const m = useAuthRefreshMiddleware();
    await m.post!(makeContext(401));

    expect(mockSignIn).toHaveBeenCalledWith("oidc", expect.objectContaining({ redirect: true }));
  });

  it("shares one in-flight refresh for concurrent 401 responses", async () => {
    let resolveRefresh!: () => void;
    const refreshPromise = new Promise<void>(r => {
      resolveRefresh = r;
    });
    mockRefresh.mockReturnValue(refreshPromise);

    const retryResponse = new Response(null, { status: 200 });
    const fetchFn = vi.fn().mockResolvedValue(retryResponse);

    const m = useAuthRefreshMiddleware();
    const p1 = m.post!(makeContext(401, fetchFn));
    const p2 = m.post!(makeContext(401, fetchFn));

    resolveRefresh();
    await Promise.all([p1, p2]);

    // Only one refresh call, not two
    expect(mockRefresh).toHaveBeenCalledOnce();
  });
});
