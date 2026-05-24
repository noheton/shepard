import type { Middleware, ResponseContext } from "@dlr-shepard/backend-client";
import { useV1Deprecation } from "~/composables/context/useV1Deprecation";

/**
 * V1COMPAT.0 — middleware for the generated API client that
 * inspects every response for the {@code X-Shepard-Legacy: true}
 * header emitted by the backend's
 * {@code LegacyV1DeprecationFilter}. When seen, it bumps the
 * session-scoped counter the {@code V1DeprecationBanner} watches.
 *
 * <p>Idempotent + side-effect-only: never modifies the response,
 * never aborts the chain. Safe to chain alongside
 * {@link useAuthRefreshMiddleware} regardless of order — both run
 * as {@code post} hooks, and their concerns don't overlap.
 *
 * <p>Call this inside a composable/component setup so the
 * underlying {@code useV1Deprecation} module state is reachable.
 */
export function useV1DeprecationMiddleware(): Middleware {
  const { recordResponse } = useV1Deprecation();

  async function post(context: ResponseContext): Promise<Response | void> {
    // The generated runtime's Response is a standard Fetch Response,
    // so headers are accessible via the Headers interface.
    if (context && context.response && context.response.headers) {
      recordResponse(context.response.headers);
    }
    // No return → leave the response untouched (the runtime accepts void).
  }

  return { post };
}
