import type { Middleware, ResponseContext } from "@dlr-shepard/backend-client";

import {
  classifyRoleChangedBody,
  useStaleRoleSession,
} from "~/composables/context/useStaleRoleSession";

/**
 * Returns a `Middleware` for the generated API client that intercepts 401
 * responses, attempts a single token refresh via nuxt-auth's `refresh()`, and
 * retries the original request with the fresh token.
 *
 * If the retry also returns 401, the user is redirected to `/auth/signIn`
 * with a `callbackUrl` so they land back on the current page after re-auth.
 *
 * Concurrent 401 responses share a single in-flight refresh promise to avoid
 * triggering multiple simultaneous refresh exchanges.
 *
 * ROLE-GRANT-STALE-SESSION-02: the middleware also peeks at the 401 body
 * before refreshing. When the body carries `exception: "role_changed"`,
 * the global `useStaleRoleSession()` flag is flipped so any
 * `UnauthorizedView` rendered after this point upgrades its hint copy
 * from the speculative -03 default ("did you just get the grant?") to the
 * definitive "your role just changed". The flag survives the refresh-
 * and-retry cycle because the Neo4j-side role grant may not yet be
 * reflected in the IdP claims that the new token carries.
 *
 * Call this inside a composable/component setup so `useAuth()` and
 * `useRouter()` resolve from the active Nuxt app context.
 */
export function useAuthRefreshMiddleware(): Middleware {
  const { refresh, data, signIn } = useAuth();
  const router = useRouter();
  const { set: setStaleRoleReason } = useStaleRoleSession();

  // Deduplicates concurrent 401 responses within a single middleware instance.
  // Note: each useShepardApi / useV2ShepardApi call creates its own instance, so
  // two components using different API classes can still trigger two parallel
  // refreshes. In practice this is idempotent (Keycloak accepts duplicate
  // refresh requests) and rare enough that a module-level singleton is not
  // needed for v1.
  let inFlightRefresh: Promise<unknown> | null = null;

  async function handleUnauthorized(context: ResponseContext): Promise<Response | undefined> {
    if (context.response.status !== 401) return;

    // ROLE-GRANT-STALE-SESSION-02 — peek at the body BEFORE refresh so the
    // shared flag is set whether or not refresh-and-retry succeeds. Clone
    // the response so the caller can still read the body downstream
    // (`response.json()` on a stream is one-shot). A non-JSON body or a
    // body that doesn't match the role_changed shape silently falls
    // through — no-op.
    try {
      const clone = context.response.clone();
      const parsed = await clone.json();
      const reason = classifyRoleChangedBody(parsed);
      if (reason) setStaleRoleReason(reason);
    } catch {
      // Body wasn't JSON / already consumed — fall through silently. The
      // role_changed signal is best-effort hint metadata; failing to read
      // it must not block the auth-refresh flow.
    }

    try {
      if (!inFlightRefresh) {
        inFlightRefresh = refresh().finally(() => {
          inFlightRefresh = null;
        });
      }
      await inFlightRefresh;
    } catch {
      // Refresh failed — send to sign-in with callback.
      const callbackUrl = encodeURIComponent(router.currentRoute.value.fullPath);
      await signIn("oidc", { callbackUrl, redirect: true });
      return;
    }

    const newToken = data.value?.accessToken;
    if (!newToken) {
      const callbackUrl = encodeURIComponent(router.currentRoute.value.fullPath);
      await signIn("oidc", { callbackUrl, redirect: true });
      return;
    }

    // Clone the original init and inject the fresh token.
    const newInit: RequestInit = {
      ...context.init,
      headers: {
        ...context.init.headers,
        Authorization: `Bearer ${newToken}`,
      },
    };
    const retryResponse = await context.fetch(context.url, newInit);
    if (retryResponse.status === 401) {
      // Refresh token also expired or revoked — sign in fresh.
      const callbackUrl = encodeURIComponent(router.currentRoute.value.fullPath);
      await signIn("oidc", { callbackUrl, redirect: true });
      return;
    }
    return retryResponse;
  }

  return { post: handleUnauthorized };
}
