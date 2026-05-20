/**
 * ChunkLoadError recovery guard.
 *
 * When a lazy-loaded chunk 404s (because a deploy rotated hashes while
 * the user had the tab open), we want the page to recover — but not
 * silently.  Calling window.location.reload() mid-session is jarring:
 * the user loses unsaved state and has no idea why.
 *
 * Instead we delegate to useStaleBundle().triggerChunkReload() which
 * shows the StaleBundleBanner with a 3-second countdown and a "Later"
 * escape hatch.  If the user clicks Later they stay on the (potentially
 * broken) page; on the next navigation Nuxt's own
 * `emitRouteChunkError: "automatic"` catches the next failure.
 *
 * This plugin targets the complementary gap to emitRouteChunkError:
 * dynamic imports that happen outside navigation (inside onMounted,
 * composables, or <ClientOnly> wrappers) which surface as unhandled
 * promise rejections rather than Nuxt router events.
 */
import { useStaleBundle } from "~/composables/layout/useStaleBundle";

const RELOAD_KEY = "shepard:chunkErrorReloaded";

function isChunkError(err: unknown): boolean {
  if (typeof err !== "object" || err === null) return false;
  const msg = (err as { message?: string }).message ?? "";
  return msg.includes("Loading chunk") || msg.includes("ChunkLoadError");
}

export default defineNuxtPlugin(nuxtApp => {
  if (typeof window === "undefined") return;

  const { triggerChunkReload } = useStaleBundle();

  // Unhandled chunk errors from dynamic imports outside the router.
  window.addEventListener("unhandledrejection", event => {
    if (!isChunkError(event.reason)) return;
    // Guard against an infinite reload loop: if the countdown already fired
    // once this session (banner "Later" was clicked, or the page recovered),
    // fall back to a silent reload after a short wait.
    if (sessionStorage.getItem(RELOAD_KEY)) {
      sessionStorage.removeItem(RELOAD_KEY);
      return;
    }
    sessionStorage.setItem(RELOAD_KEY, "1");
    triggerChunkReload();
  });

  // Chunk errors during route navigation (Nuxt's router-level path).
  // emitRouteChunkError is set to "automatic" for navigation, but we
  // also hook "app:chunkError" so we can show the banner before Nuxt
  // decides what to do.
  nuxtApp.hook("app:chunkError", () => {
    triggerChunkReload();
  });

  // Clear the guard after a successful page load.
  nuxtApp.hook("page:finish", () => {
    sessionStorage.removeItem(RELOAD_KEY);
  });
});
