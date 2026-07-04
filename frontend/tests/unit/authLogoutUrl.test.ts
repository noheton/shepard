/**
 * BUG-SIGNOUT-KC-SSO-LINGERS — proves the `/api/auth-logout-url` Nitro
 * endpoint resolves Keycloak's `end_session_endpoint` from the well-known
 * config and returns it to the browser. The browser uses the returned URL
 * to drive a client-side redirect that clears Keycloak's SSO cookie.
 *
 * The fix is structural: the cookie can only be cleared by a *browser*
 * navigation to Keycloak — a server-side `fetch()` from a serverless
 * function never sees the user's browser cookie store. This test pins
 * the resolver shape so a future refactor doesn't accidentally regress to
 * the server-side-fetch antipattern.
 */
import { describe, it, expect, vi } from "vitest";

const mockJson = vi.fn().mockResolvedValue({
  token_endpoint: "https://kc.example/realms/x/protocol/openid-connect/token",
  end_session_endpoint:
    "https://kc.example/realms/x/protocol/openid-connect/logout",
});
const mockFetch = vi.fn().mockResolvedValue({
  ok: true,
  statusText: "OK",
  json: mockJson,
});

(globalThis as unknown as Record<string, unknown>).fetch = mockFetch;
(globalThis as unknown as Record<string, unknown>).defineEventHandler = (
  fn: () => unknown,
) => fn;
(globalThis as unknown as Record<string, unknown>).useRuntimeConfig = () => ({
  oidcIssuer: "https://kc.example/realms/x/",
});

describe("/api/auth-logout-url — BUG-SIGNOUT-KC-SSO-LINGERS", () => {
  it("returns Keycloak's end_session_endpoint as a string", async () => {
    // Dynamic import so the module evaluates after the globals are
    // installed (Nitro auto-imports are not available under plain Vitest).
    const mod = await import("~/server/api/auth-logout-url.get");
    const handler = mod.default as () => Promise<{ url: string }>;
    const result = await handler();
    expect(result.url).toBe(
      "https://kc.example/realms/x/protocol/openid-connect/logout",
    );
    expect(mockFetch).toHaveBeenCalled();
  });
});
