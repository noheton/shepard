import { DataObjectApi, type ResponseError } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

// Module-level TTL cache so repeated component mounts (navigate away → back)
// share the same reactive Map and don't re-fetch within the TTL window.
const TTL_MS = 5 * 60 * 1000; // 5 minutes

interface CacheEntry {
  map: Ref<Map<number, string>>;
  fetchedAt: number;
  promise: Promise<void> | null;
}

const cache = new Map<number, CacheEntry>();

// Accepts a plain number, a Ref, or a getter. The numeric id is resolved at
// fetch time so callers driven by an async-loaded v2 collection (where the
// route param is the appId UUID, not the numeric id) work correctly —
// BUG-COLL-APPID-ROUTE-007-PAGE.
export function useFetchDataObjectMapByCollection(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
) {
  // Stable proxy map that always mirrors the currently-resolved collection's
  // cache entry, so the returned ref identity never changes across id updates.
  const dataObjectsMap = ref<Map<number, string>>(new Map<number, string>());

  function resolvedId(): number | undefined {
    return toValue(collectionIdInput);
  }

  function entryFor(id: number): CacheEntry {
    if (!cache.has(id)) {
      cache.set(id, {
        map: ref<Map<number, string>>(new Map<number, string>()),
        fetchedAt: 0,
        promise: null,
      });
    }
    return cache.get(id)!;
  }

  function fetchMap(): Promise<void> {
    const collectionId = resolvedId();
    if (collectionId == null) return Promise.resolve();
    const entry = entryFor(collectionId);
    // Mirror this entry's map into the stable proxy on every fetch.
    dataObjectsMap.value = entry.map.value;
    const now = Date.now();
    // Return in-flight promise if one is running
    if (entry.promise !== null) {
      return entry.promise;
    }
    // Return immediately if cache is fresh
    if (entry.fetchedAt > 0 && now - entry.fetchedAt < TTL_MS) {
      return Promise.resolve();
    }
    // Kick a new fetch
    entry.promise = useShepardApi(DataObjectApi)
      .value.getAllDataObjects({ collectionId })
      .then(response => {
        entry.map.value = new Map(response.map(d => [d.id, d.name]));
        entry.fetchedAt = Date.now();
        // Re-mirror into the proxy now that the fresh map exists.
        if (resolvedId() === collectionId) dataObjectsMap.value = entry.map.value;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching dataobjects");
      })
      .finally(() => {
        entry.promise = null;
      });
    return entry.promise;
  }

  return { dataObjectsMap, fetchMap };
}
