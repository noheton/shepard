/**
 * Shared singleton state for the StaleBundleBanner.
 *
 * Module-level refs (not inject-based) so this can be imported from
 * Nuxt plugins and client-side utility code that runs outside setup().
 */

// Version-mismatch path (backend version poll detected a new build).
const _versionStale = ref(false);
const _initial = ref<string | null>(null);
const _latest = ref<string | null>(null);

// Chunk-error path: a route or dynamic import 404'd because the deploy
// rotated hashes while the user was on the page. We show the banner for
// RELOAD_DELAY_MS, then hard-reload so the page recovers.
const _pendingReload = ref(false);
const _reloadCountdown = ref(0);
const RELOAD_DELAY_MS = 3000;

// Per-tab dismiss (cleared on banner re-trigger).
const _dismissed = ref(false);

let _countdownTimer: ReturnType<typeof setInterval> | null = null;

export function useStaleBundle() {
  const show = computed(
    () => (_versionStale.value || _pendingReload.value) && !_dismissed.value,
  );

  function setVersions(initial: string, latest: string) {
    if (initial === latest) return;
    _initial.value = initial;
    _latest.value = latest;
    _versionStale.value = true;
    _dismissed.value = false;
  }

  function initVersion(v: string) {
    if (_initial.value === null) {
      _initial.value = v;
      _latest.value = v;
    }
  }

  function triggerChunkReload() {
    if (_pendingReload.value) return; // already counting down
    _pendingReload.value = true;
    _dismissed.value = false;
    _reloadCountdown.value = Math.ceil(RELOAD_DELAY_MS / 1000);
    _countdownTimer = setInterval(() => {
      _reloadCountdown.value -= 1;
      if (_reloadCountdown.value <= 0) {
        if (_countdownTimer) clearInterval(_countdownTimer);
        if (typeof window !== "undefined") window.location.reload();
      }
    }, 1000);
  }

  function dismiss() {
    _dismissed.value = true;
    // If a reload is pending, cancel it — user clicked "Later".
    // They'll have to deal with the potentially-broken chunk themselves.
    if (_countdownTimer) {
      clearInterval(_countdownTimer);
      _countdownTimer = null;
    }
    _pendingReload.value = false;
  }

  function refresh() {
    if (typeof window !== "undefined") window.location.reload();
  }

  return {
    show,
    initial: _initial,
    latest: _latest,
    versionStale: _versionStale,
    pendingReload: _pendingReload,
    reloadCountdown: _reloadCountdown,
    setVersions,
    initVersion,
    triggerChunkReload,
    dismiss,
    refresh,
  };
}
