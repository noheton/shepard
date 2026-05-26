import { DataObjectApi, DataObjectV2Api, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
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
 * Note on v1 vs v2: the first consumer to resolve the cache entry determines
 * the fetch path. Lineage (which passes collectionAppId) goes via v2 and gets
 * per-kind ref counts; provenance (which omits collectionAppId) goes via v1.
 * Neither consumer reads the extra ref-count fields today, so this is fine.
 * When both are on the same page the first-caller wins; the second re-uses
 * the same data regardless of which path resolved it.
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
 * When `collectionAppId` is provided the v2 endpoint is used, which includes
 * per-kind reference counts (`timeseriesCount`, `fileCount`, `structuredDataCount`).
 * Falls back to the v1 endpoint (numeric collectionId only) when appId is not yet
 * available (e.g. before the Collection has loaded).
 */
export function useFetchAllDataObjects(collectionId: number, collectionAppId?: Ref<string | null>) {
  const v2Api = useV2ShepardApi(DataObjectV2Api);
  const v1Api = useShepardApi(DataObjectApi);

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
    const appId = collectionAppId?.value ?? null;
    entry.loading.value = true;
    try {
      if (appId) {
        const PAGE = 200;
        let page = 0;
        const results: DataObjectListItemV2[] = [];
        while (true) {
          const batch = await v2Api.value.listDataObjects({
            collectionAppId: appId,
            page,
            size: PAGE,
          });
          results.push(...batch);
          if (batch.length < PAGE) break;
          page++;
        }
        entry.dataObjects.value = results;
      } else {
        const PAGE = 200;
        let page = 0;
        const results: DataObjectListItemV2[] = [];
        while (true) {
          const batch = await v1Api.value.getAllDataObjects({
            collectionId,
            page,
            size: PAGE,
          });
          results.push(...(batch as DataObjectListItemV2[]));
          if (batch.length < PAGE) break;
          page++;
        }
        entry.dataObjects.value = results;
      }
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

  if (collectionAppId) {
    // Re-fetch whenever the appId materialises (initial null → uuid transition).
    watch(collectionAppId, (appId) => {
      if (appId !== null) {
        // If the entry was populated via v1 before appId arrived, keep the
        // data but mark it stale so v2 supersedes it on the next watcher tick.
        entry.fetchedAt = 0;
        ensureFetched(entry);
      }
    }, { immediate: true });
  } else {
    ensureFetched(entry);
  }

  return { dataObjects: entry.dataObjects, loading: entry.loading };
}
