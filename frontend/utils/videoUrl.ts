/**
 * MFFD-VIDEOREF-SCALE-1 — URL helpers for the HTML5 `<video>` element.
 *
 * The browser cannot inject custom headers on `<video src>` (only
 * `XMLHttpRequest` / `fetch` can). To let the player do its own
 * Range requests on a JWT-protected backend, we travel the token as
 * `?access_token=…` per RFC 6750 §2.3. `JWTFilter` reads the query
 * param as a fallback when no Authorization header is present.
 *
 * The helper is extracted out of `VideoPlayer.vue` for the same
 * reason `referenceTemplatePrefill.ts` exists — the Vitest harness
 * here does not wire `@vue/test-utils`, and a pure-function helper is
 * cheap to cover.
 */

/**
 * Return the URL with `?access_token=…` appended. Existing query
 * parameters on the URL survive (signed-URL surfaces, future
 * `?range=` proxy paths, etc.).
 *
 * - If `accessToken` is null / empty, the URL is returned unchanged.
 * - URL-construction failures (relative-path corner cases) fall
 *   back to a manual `?` / `&` append, encoded.
 *
 * @param rawUrl     the source URL (relative or absolute)
 * @param accessToken the JWT to append, or null to skip
 * @param origin     the origin to resolve relative URLs against
 *                   (defaults to `window.location.origin` in browsers;
 *                   callers in a non-DOM context pass it explicitly)
 */
export function withAccessTokenQueryParam(
  rawUrl: string,
  accessToken?: string | null,
  origin?: string,
): string {
  if (!accessToken) return rawUrl;
  try {
    const resolvedOrigin =
      origin ??
      (typeof window !== "undefined" ? window.location.origin : "http://localhost");
    const u = new URL(rawUrl, resolvedOrigin);
    u.searchParams.set("access_token", accessToken);
    // If the input was absolute, returning u.toString() preserves the
    // absolute shape. If the input was relative, the URL constructor
    // adds the origin — pull back to a path so we don't accidentally
    // rewrite the origin for callers that compose against a custom base.
    if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
      return u.toString();
    }
    return u.pathname + u.search + u.hash;
  } catch {
    // Relative URL with weird shape; fall back to manual append.
    const sep = rawUrl.includes("?") ? "&" : "?";
    return `${rawUrl}${sep}access_token=${encodeURIComponent(accessToken)}`;
  }
}
