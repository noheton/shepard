/**
 * BUG-SIGNOUT-KC-SSO-LINGERS — expose Keycloak's `end_session_endpoint` to
 * the browser so sign-out can be performed as a client-side redirect.
 *
 * Previously the nuxt-auth `events.signOut` callback fetched the
 * end_session URL server-side. That cleared the Keycloak server-side
 * session but left the user's browser SSO cookie intact — a subsequent
 * sign-in silently re-authed against the warm SSO session with no
 * password prompt (operator-surfaced 2026-05-31).
 *
 * Fix: the browser hits `end_session_endpoint` itself with
 * `id_token_hint` + `post_logout_redirect_uri`. Keycloak then clears the
 * browser SSO cookie and bounces the user to the post-logout URL.
 *
 * This endpoint resolves the canonical end_session URL server-side from
 * `oidcIssuer/.well-known/openid-configuration` so the SPA never needs
 * the issuer URL baked in.
 *
 * Lives at `/api/auth-logout-url` (NOT under `/api/auth/*` because the
 * nuxt-auth catch-all `[...].ts` handler owns that path family).
 */
import { getLogoutUrl } from "~/server/utils/oidc-configuration-helper";

export default defineEventHandler(async () => {
  const runtimeConfig = useRuntimeConfig();
  const wellKnown = new URL(
    `${runtimeConfig.oidcIssuer}.well-known/openid-configuration`,
  );
  const logoutUrl = await getLogoutUrl(wellKnown);
  return { url: logoutUrl.toString() };
});
