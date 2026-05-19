/**
 * ChunkLoadError reload guard.
 *
 * `nuxt.config.ts` already sets `experimental.emitRouteChunkError: "automatic"`
 * which handles the navigation-path chunk 404 (one automatic reload on the
 * next route push).  This plugin targets the complementary gap: dynamic
 * imports that happen outside navigation — inside `onMounted` callbacks,
 * composables, or `<ClientOnly>` wrappers — which surface as unhandled
 * promise rejections rather than Nuxt router events.
 *
 * Guard logic:
 *   1. First ChunkLoadError this session → set a sessionStorage flag and
 *      reload.  The hard reload forces the browser to fetch the new HTML
 *      index (no-store cache policy) and pick up the current chunk hashes.
 *   2. If the flag is already set we already reloaded once.  Give up to
 *      avoid an infinite reload loop.  The StaleBundleBanner will appear on
 *      the next version-poll cycle to prompt a manual refresh.
 *   3. Clear the flag on every successful full navigation (`page:finish`) so
 *      a genuine ChunkLoadError on a later visit is not silently suppressed.
 */

const RELOAD_KEY = "shepard:chunkErrorReloaded";

function isChunkError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const msg = (err as { message?: string }).message ?? "";
  return msg.includes("Loading chunk") || msg.includes("ChunkLoadError");
}

export default defineNuxtPlugin(nuxtApp => {
  if (typeof window === "undefined") return;

  window.addEventListener("unhandledrejection", event => {
    if (!isChunkError(event.reason)) return;
    if (sessionStorage.getItem(RELOAD_KEY)) return; // already reloaded once
    sessionStorage.setItem(RELOAD_KEY, "1");
    window.location.reload();
  });

  // Clear the guard after a successful navigation so transient errors on
  // subsequent visits don't get incorrectly blocked.
  nuxtApp.hook("page:finish", () => {
    sessionStorage.removeItem(RELOAD_KEY);
  });
});
