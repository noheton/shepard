import type { Middleware, ResponseContext } from "@dlr-shepard/backend-client";

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
 * Call this inside a composable/component setup so `useAuth()` and
 * `useRouter()` resolve from the active Nuxt app context.
 */
export function useAuthRefreshMiddleware(): Middleware {
  const { refresh, data, signIn } = useAuth();
  const router = useRouter();

  // Deduplicates concurrent 401 responses within a single middleware instance.
  // Note: each useShepardApi / useV2ShepardApi call creates its own instance, so
  // two components using different API classes can still trigger two parallel
  // refreshes. In practice this is idempotent (Keycloak accepts duplicate
  // refresh requests) and rare enough that a module-level singleton is not
  // needed for v1.
  let inFlightRefresh: Promise<unknown> | null = null;

  async function handleUnauthorized(context: ResponseContext): Promise<Response | void> {
    if (context.response.status !== 401) return;

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
