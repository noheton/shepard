import { DataObjectsApi, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * PERF6 — singleton cache keyed by numeric collectionId.
 *
 * Two consumers on the same page (or same navigation frame) with the same
 * collectionId share one reactive ref and one set of paginated round-trips.
 *
 * Cache entries expire after CACHE_TTL_MS (5 minutes). Call
 * invalidateDataObjectsCache(collectionId) from mutation paths (create /
 * delete / update) to force an immediate refetch on the next consumer.
 *
 * V2-SWEEP Wave 4: v2-only. The list always loads from
 * GET /v2/collections/{collectionAppId}/data-objects — when the caller has
 * no appId yet, the numeric id is stringified into the same path (the
 * backend EntityIdResolver accepts both shapes), so the v1
 * getAllDataObjects fallback is gone.
 */

const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

interface CacheEntry {
  dataObjects: Ref<DataObjectListItemV2[]>;
  loading: Ref<boolean>;
  /** Resolves when the in-flight fetch completes (null if idle). */
  inflight: Promise<void> | null;
  fetchedAt: number;
}

// Module-level map — survives component mount/unmount within a navigation.
const _cache = new Map<number, CacheEntry>();

/** Exposed for tests only — clears the module-level cache. */
export function _resetDataObjectsCacheForTests(): void {
  _cache.clear();
}

/**
 * Invalidate a single collection entry (or the entire cache when called
 * without arguments). The next consumer call will trigger a fresh fetch.
 */
export function invalidateDataObjectsCache(collectionId?: number): void {
  if (collectionId !== undefined) {
    _cache.delete(collectionId);
  } else {
    _cache.clear();
  }
}

/**
 * Fetches all DataObjects for a Collection.
 *
 * Results are shared across all callers with the same `collectionId` within
 * a single navigation context (PERF6 — avoids 2 × ceil(N/200) independent
 * paginated round-trips when CollectionLineageGraph and DataObjectProvGraph
 * are both mounted for the same collection).
 *
 * Always v2 (V2-SWEEP Wave 4): the collection identifier on the wire is the
 * appId when available, otherwise the stringified numeric id — both resolve
 * via the backend EntityIdResolver on the same appId-keyed endpoint.
 */
export function useFetchAllDataObjects(collectionId: number, collectionAppId?: Ref<string | null>) {
  const v2Api = useV2ShepardApi(DataObjectsApi);

  // Return or create the cache entry for this collectionId.
  function getOrCreateEntry(): CacheEntry {
    const existing = _cache.get(collectionId);
    if (existing) return existing;
    const entry: CacheEntry = {
      dataObjects: ref<DataObjectListItemV2[]>([]),
      loading: ref(false),
      inflight: null,
      fetchedAt: 0,
    };
    _cache.set(collectionId, entry);
    return entry;
  }

  async function fetchAll(entry: CacheEntry): Promise<void> {
    // appId when available, else the stringified numeric id — same v2 path.
    const identifier = collectionAppId?.value ?? String(collectionId);
    entry.loading.value = true;
    try {
      const PAGE = 200;
      let page = 0;
      const results: DataObjectListItemV2[] = [];
      while (true) {
        const batch = await v2Api.value.listDataObjects({
          collectionAppId: identifier,
          page,
          pageSize: PAGE,
        });
        results.push(...batch);
        if (batch.length < PAGE) break;
        page++;
      }
      entry.dataObjects.value = results;
      entry.fetchedAt = Date.now();
    } catch (e) {
      // On error keep any previously cached data rather than wiping it.
      // Only clear when the cache is genuinely empty (first load failure).
      if (entry.fetchedAt === 0) {
        entry.dataObjects.value = [];
      }
      handleError(e, "fetchAllDataObjects");
    } finally {
      entry.loading.value = false;
      entry.inflight = null;
    }
  }

  function ensureFetched(entry: CacheEntry): void {
    const age = Date.now() - entry.fetchedAt;
    const stale = entry.fetchedAt === 0 || age > CACHE_TTL_MS;

    if (!stale) return;            // fresh data — no-op
    if (entry.inflight) return;    // fetch already in flight — attach silently

    entry.inflight = fetchAll(entry);
  }

  const entry = getOrCreateEntry();

  // Both identifier shapes hit the same v2 endpoint, so no refetch is needed
  // when the appId materialises later — the numeric-string fetch already
  // returned the same rows.
  ensureFetched(entry);

  return { dataObjects: entry.dataObjects, loading: entry.loading };
}
