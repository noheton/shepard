import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ResponseContext } from "@dlr-shepard/backend-client";
import { useAuthRefreshMiddleware } from "~/composables/common/api/useAuthRefreshMiddleware";

// useAuthRefreshMiddleware reads useAuth/useRouter from the global scope only
// when its returned function is invoked (inside it() blocks), so installing
// these stubs at module top is sufficient.
const mockRefresh = vi.fn();
const mockSignIn = vi.fn();
const mockDataRef = ref<{ accessToken: string } | null>(null);
const mockStatusRef = ref<"authenticated" | "unauthenticated" | "loading">(
  "authenticated",
);
const mockCurrentRoute = ref({ fullPath: "/test", path: "/test" });

(globalThis as unknown as Record<string, unknown>).useAuth = () => ({
  refresh: mockRefresh,
  data: mockDataRef,
  status: mockStatusRef,
  signIn: mockSignIn,
});
(globalThis as unknown as Record<string, unknown>).useRouter = () => ({
  currentRoute: mockCurrentRoute,
});
// ROLE-GRANT-STALE-SESSION-02 — the middleware consumes `useStaleRoleSession()`
// which calls Nuxt's auto-imported `useState`. For unit tests we give it a
// per-key ref shim so the composable's Nuxt-side state machinery doesn't need
// to be live.
const stateStore = new Map<string, ReturnType<typeof ref>>();
(globalThis as unknown as Record<string, unknown>).useState = <T>(
  key: string,
  init: () => T,
) => {
  if (!stateStore.has(key)) stateStore.set(key, ref(init()));
  return stateStore.get(key)!;
};

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
  mockStatusRef.value = "authenticated";
  mockCurrentRoute.value = { fullPath: "/test", path: "/test" };
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

  // BUG-SIGNOUT-LOOP-1 regression coverage (operator report 2026-05-31).
  describe("post-signout suppression (BUG-SIGNOUT-LOOP-1)", () => {
    it("does NOT call signIn when accessToken is absent (post-signout)", async () => {
      // After sign-out the next-auth session is cleared. Lingering API calls
      // from components that fire unconditionally (HeaderBar profile fetch,
      // notifications poller) used to re-trigger signIn() here and ping-pong
      // the browser between `/` and `/auth/signIn`.
      mockDataRef.value = null;
      mockStatusRef.value = "unauthenticated";
      mockCurrentRoute.value = { fullPath: "/", path: "/" };

      const m = useAuthRefreshMiddleware();
      const result = await m.post!(makeContext(401));

      expect(mockSignIn).not.toHaveBeenCalled();
      expect(mockRefresh).not.toHaveBeenCalled();
      expect(result).toBeUndefined();
    });

    it("does NOT call signIn when status is 'unauthenticated' even if a stale token lingers", async () => {
      // Race: token cookie cleared but JWT shape still in memory briefly.
      mockDataRef.value = { accessToken: "stale-cached-token" };
      mockStatusRef.value = "unauthenticated";

      const m = useAuthRefreshMiddleware();
      await m.post!(makeContext(401));

      expect(mockSignIn).not.toHaveBeenCalled();
    });

    it("does NOT call signIn when sitting on the landing page '/'", async () => {
      // Belt-and-braces: the public landing page never re-launches OIDC; the
      // user landed there deliberately and should not be bounced away.
      mockDataRef.value = null;
      mockCurrentRoute.value = { fullPath: "/", path: "/" };

      const m = useAuthRefreshMiddleware();
      await m.post!(makeContext(401));

      expect(mockSignIn).not.toHaveBeenCalled();
    });

    it("does NOT call signIn when sitting on '/auth/signIn'", async () => {
      mockDataRef.value = null;
      mockCurrentRoute.value = { fullPath: "/auth/signIn", path: "/auth/signIn" };

      const m = useAuthRefreshMiddleware();
      await m.post!(makeContext(401));

      expect(mockSignIn).not.toHaveBeenCalled();
    });

    it("still calls signIn when the session has a token but a protected call 401s twice", async () => {
      // The positive case: real expired-token scenario on a protected route
      // must still bounce to signIn.
      mockDataRef.value = { accessToken: "refreshed-but-server-rejects" };
      mockStatusRef.value = "authenticated";
      mockCurrentRoute.value = {
        fullPath: "/collections/123",
        path: "/collections/123",
      };
      const fetchFn = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));

      const m = useAuthRefreshMiddleware();
      await m.post!(makeContext(401, fetchFn));

      expect(mockSignIn).toHaveBeenCalledWith(
        "oidc",
        expect.objectContaining({ redirect: true }),
      );
    });
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

  // ROLE-GRANT-STALE-SESSION-02 — body-shape inspection sets the shared flag.
  describe("role_changed body inspection (ROLE-GRANT-STALE-SESSION-02)", () => {
    function makeRetryFetch() {
      // After body inspection the middleware always proceeds to refresh +
      // retry; mock the retry fetch to return a 200 so the post-retry path
      // (`retryResponse.status === 401`) doesn't NPE on undefined.
      return vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    }

    function makeContextWithBody(body: unknown): ResponseContext {
      const response = new Response(JSON.stringify(body), {
        status: 401,
        headers: { "content-type": "application/json" },
      });
      return {
        response,
        fetch: makeRetryFetch(),
        url: "https://api.example.com/v2/admin/features",
        init: { headers: { Authorization: "Bearer stale-token" } },
      } as unknown as ResponseContext;
    }

    it("flips the staleRoleSession flag when 401 body carries role_changed", async () => {
      // Pre-condition: fresh test; flag starts null.
      stateStore.delete("stale-role-session-reason");

      const m = useAuthRefreshMiddleware();
      await m.post!(
        makeContextWithBody({
          status: 401,
          exception: "role_changed",
          message: "Your session was issued before a role change.",
        }),
      );

      const flag = stateStore.get("stale-role-session-reason");
      expect(flag?.value).toBe("role-changed");
    });

    it("does NOT flip the flag for a generic 401 body", async () => {
      stateStore.delete("stale-role-session-reason");

      const m = useAuthRefreshMiddleware();
      await m.post!(
        makeContextWithBody({
          status: 401,
          exception: "AuthenticationException",
          message: "Invalid token",
        }),
      );

      const flag = stateStore.get("stale-role-session-reason");
      // Flag was created (lazy init by useStaleRoleSession) but not set.
      expect(flag?.value).toBeNull();
    });

    it("does not throw when the 401 body is not JSON", async () => {
      stateStore.delete("stale-role-session-reason");
      const response = new Response("plain text body", {
        status: 401,
        headers: { "content-type": "text/plain" },
      });
      const ctx = {
        response,
        fetch: makeRetryFetch(),
        url: "https://api.example.com/x",
        init: { headers: { Authorization: "Bearer token" } },
      } as unknown as ResponseContext;

      const m = useAuthRefreshMiddleware();
      await expect(m.post!(ctx)).resolves.not.toThrow();
    });
  });
});
