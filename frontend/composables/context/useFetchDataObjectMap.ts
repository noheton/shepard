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

export function useFetchDataObjectMapByCollection(collectionId: number) {
  if (!cache.has(collectionId)) {
    cache.set(collectionId, {
      map: ref<Map<number, string>>(new Map<number, string>()),
      fetchedAt: 0,
      promise: null,
    });
  }

  const entry = cache.get(collectionId)!;
  const dataObjectsMap = entry.map;

  function fetchMap(): Promise<void> {
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
