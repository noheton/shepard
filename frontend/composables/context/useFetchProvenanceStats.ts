import {
  ProvenanceApi,
  type ProvenanceStatsIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * Fetches the per-Collection (or per-user / instance) provenance stats payload
 * from `GET /v2/provenance/stats`. Drives the activity sparkline dashboard
 * (PROV1d, see `aidocs/55 §5`).
 *
 * The backend already gates `scope=collection` on the caller's `Read`
 * permission against the Collection (PROV1c-acl). 401 / 403 responses
 * surface through the standard `handleError` channel; the composable
 * leaves `stats` as `null` so the component can render its empty state.
 *
 * Default window: last 30 days. Callers can pass an explicit `sinceMillis`
 * to widen / narrow the window via the dashboard's time-range picker.
 */
export interface UseFetchProvenanceStatsOptions {
  /**
   * `collection` | `user` | `instance`. Required.
   * Most callers pass `"collection"`.
   */
  scope: string;
  /**
   * appId (for `scope=collection`) or username (for `scope=user`).
   * Ignored for `scope=instance`.
   */
  id?: string;
  /**
   * Inclusive lower bound on `startedAt` (millis since epoch). Defaults
   * to "30 days ago" when omitted.
   */
  sinceMillis?: number;
  /**
   * Inclusive upper bound on `startedAt` (millis since epoch). Defaults
   * to "now" when omitted.
   */
  untilMillis?: number;
}

const THIRTY_DAYS_MILLIS = 30 * 24 * 60 * 60 * 1000;

export function useFetchProvenanceStats(opts: UseFetchProvenanceStatsOptions) {
  const stats = ref<ProvenanceStatsIO | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  // Reactive copy of the time window so the component can mutate it via the
  // picker without re-creating the composable.
  const sinceMillis = ref<number | undefined>(opts.sinceMillis);
  const untilMillis = ref<number | undefined>(opts.untilMillis);

  function effectiveSince(): number {
    if (sinceMillis.value !== undefined) return sinceMillis.value;
    return Date.now() - THIRTY_DAYS_MILLIS;
  }

  function refresh(nextSinceMillis?: number, nextUntilMillis?: number) {
    if (nextSinceMillis !== undefined) sinceMillis.value = nextSinceMillis;
    if (nextUntilMillis !== undefined) untilMillis.value = nextUntilMillis;

    // scope=collection / scope=user require an id; bail out cleanly so we
    // don't bombard the backend with 400s while the parent page is still
    // hydrating the Collection.
    if (
      (opts.scope === "collection" || opts.scope === "user") &&
      !opts.id
    ) {
      stats.value = null;
      return;
    }

    isLoading.value = true;
    error.value = null;
    useV2ShepardApi(ProvenanceApi)
      .value.getStats({
        scope: opts.scope,
        id: opts.id,
        since: effectiveSince(),
        until: untilMillis.value,
      })
      .then(result => {
        stats.value = result;
      })
      .catch(e => {
        stats.value = null;
        error.value = e instanceof Error ? e.message : String(e);
        handleError(e, "fetching provenance stats");
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  refresh();

  return { stats, isLoading, error, refresh, sinceMillis, untilMillis };
}
