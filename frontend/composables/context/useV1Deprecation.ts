import { ref, computed, type Ref, type ComputedRef } from "vue";

/**
 * V1COMPAT.0 — session-scoped state for the v1 deprecation banner.
 *
 * The composable tracks whether any HTTP response observed during
 * the current browser session has carried the `X-Shepard-Legacy: true`
 * header (the fork-specific marker emitted by the
 * `LegacyV1DeprecationFilter` on every `/shepard/api/...` response).
 * When at least one v1 hit has been recorded, the banner becomes
 * visible. The state is per-session (refresh = reset) and
 * dismissible (the user can collapse the banner without losing the
 * underlying count).
 *
 * <p>Wiring: any place in the frontend that consumes a Shepard HTTP
 * response can pass the response's `headers` map (or raw Headers
 * object) to {@link recordResponse}. The function is idempotent
 * across duplicate calls for the same response — it only flips the
 * banner on; it doesn't double-count unless the caller explicitly
 * does so.
 */

const _v1HitCount = ref(0);
const _dismissed = ref(false);

export function useV1Deprecation(): {
  v1HitCount: Ref<number>;
  dismissed: Ref<boolean>;
  visible: ComputedRef<boolean>;
  recordResponse: (headers: Headers | Record<string, string | string[] | undefined> | null | undefined) => void;
  dismiss: () => void;
  reset: () => void;
} {
  const visible = computed(() => _v1HitCount.value > 0 && !_dismissed.value);

  function recordResponse(
    headers: Headers | Record<string, string | string[] | undefined> | null | undefined,
  ) {
    if (!headers) return;
    const flag = readHeader(headers, "X-Shepard-Legacy");
    if (flag && flag.toLowerCase() === "true") {
      _v1HitCount.value += 1;
    }
  }

  function dismiss() {
    _dismissed.value = true;
  }

  function reset() {
    _v1HitCount.value = 0;
    _dismissed.value = false;
  }

  return { v1HitCount: _v1HitCount, dismissed: _dismissed, visible, recordResponse, dismiss, reset };
}

/**
 * Read a single header value from either a {@link Headers} instance
 * (case-insensitive native lookup) or a plain object (which we
 * search case-insensitively to handle servers normalising header
 * names differently across HTTP runtimes).
 */
function readHeader(
  headers: Headers | Record<string, string | string[] | undefined>,
  name: string,
): string | null {
  if (typeof (headers as Headers).get === "function") {
    const v = (headers as Headers).get(name);
    return v == null ? null : v;
  }
  const obj = headers as Record<string, string | string[] | undefined>;
  const lowerName = name.toLowerCase();
  for (const k of Object.keys(obj)) {
    if (k.toLowerCase() === lowerName) {
      const v = obj[k];
      return Array.isArray(v) ? (v[0] ?? null) : (v ?? null);
    }
  }
  return null;
}
