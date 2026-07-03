import { DataObjectsApi, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * PERF6 — singleton cache keyed by collection appId (UUID v7 string).
 *
 * LINEAGE-V2: migrated from numeric `collectionId` key to string `appId` key.
 * Two consumers on the same page (or same navigation frame) with the same
 * appId share one reactive ref and one set of paginated round-trips.
 *
 * Cache entries expire after CACHE_TTL_MS (5 minutes). Call
 * invalidateDataObjectsCache(collectionAppId) from mutation paths (create /
 * delete / update) to force an immediate refetch on the next consumer.
 *
 * V2-SWEEP Wave 4: v2-only. The list always loads from
 * GET /v2/collections/{collectionAppId}/data-objects.
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
const _cache = new Map<string, CacheEntry>();

/** Exposed for tests only — clears the module-level cache. */
export function _resetDataObjectsCacheForTests(): void {
  _cache.clear();
}

/**
 * Invalidate a single collection entry (or the entire cache when called
 * without arguments). The next consumer call will trigger a fresh fetch.
 */
export function invalidateDataObjectsCache(collectionAppId?: string): void {
  if (collectionAppId !== undefined) {
    _cache.delete(collectionAppId);
  } else {
    _cache.clear();
  }
}

/**
 * Fetches all DataObjects for a Collection.
 *
 * Results are shared across all callers with the same `collectionAppId` within
 * a single navigation context (PERF6 — avoids 2 × ceil(N/200) independent
 * paginated round-trips when CollectionLineageGraph and DataObjectProvGraph
 * are both mounted for the same collection).
 *
 * LINEAGE-V2: first param is now the required string `collectionAppId`; the
 * optional second param `collectionNumericId` is carried for callers that
 * still need the numeric id internally (currently unused here — kept for
 * forward-compat with CollectionCrossTrackViewPane migration).
 */
export function useFetchAllDataObjects(collectionAppId: string, _collectionNumericId?: number) {
  const v2Api = useV2ShepardApi(DataObjectsApi);

  // Return or create the cache entry keyed by appId.
  function getOrCreateEntry(): CacheEntry {
    const existing = _cache.get(collectionAppId);
    if (existing) return existing;
    const entry: CacheEntry = {
      dataObjects: ref<DataObjectListItemV2[]>([]),
      loading: ref(false),
      inflight: null,
      fetchedAt: 0,
    };
    _cache.set(collectionAppId, entry);
    return entry;
  }

  async function fetchAll(entry: CacheEntry): Promise<void> {
    entry.loading.value = true;
    try {
      const PAGE = 200;
      let page = 0;
      const results: DataObjectListItemV2[] = [];
      while (true) {
        const batch = await v2Api.value.listDataObjects({
          collectionAppId,
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
  ensureFetched(entry);

  return { dataObjects: entry.dataObjects, loading: entry.loading };
}
