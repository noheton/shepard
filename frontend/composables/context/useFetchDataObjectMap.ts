import {
  DataObjectV2Api,
  type DataObjectListItemV2,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

// Module-level TTL cache so repeated component mounts (navigate away → back)
// share the same reactive Map and don't re-fetch within the TTL window.
const TTL_MS = 5 * 60 * 1000; // 5 minutes

interface CacheEntry {
  map: Ref<Map<number, string>>;
  fetchedAt: number;
  promise: Promise<void> | null;
}

const cache = new Map<string, CacheEntry>();

const PAGE_SIZE = 200;
const MAX_PAGES = 100;

/**
 * V2-SWEEP Wave 3: the id→name map loads from the v2 appId-keyed list
 * `GET /v2/collections/{collectionAppId}/data-objects` (paged, exhaustive,
 * fields-trimmed). The collection identifier is the route param string —
 * appId (UUID v7) or legacy numeric string, both resolved by the backend's
 * `EntityIdResolver`. No numeric-collection-id gate any more
 * (pre-Wave-3 this called the v1 `getAllDataObjects({collectionId: Long})`).
 *
 * The returned map still keys on the NUMERIC DataObject id: its consumers
 * label numeric linkage fields (predecessorIds / parentId) coming off the
 * loaded v2 entities, which is the documented-exception use of numerics
 * (resolved from payloads, never routes).
 */
export function useFetchDataObjectMapByCollection(
  collectionIdInput: MaybeRefOrGetter<string | number | undefined>,
) {
  // Stable proxy map that always mirrors the currently-resolved collection's
  // cache entry, so the returned ref identity never changes across id updates.
  const dataObjectsMap = ref<Map<number, string>>(new Map<number, string>());

  function resolvedId(): string | undefined {
    const raw = toValue(collectionIdInput);
    if (raw == null) return undefined;
    const s = String(raw);
    return s.length > 0 ? s : undefined;
  }

  function entryFor(id: string): CacheEntry {
    if (!cache.has(id)) {
      cache.set(id, {
        map: ref<Map<number, string>>(new Map<number, string>()),
        fetchedAt: 0,
        promise: null,
      });
    }
    return cache.get(id)!;
  }

  async function fetchAllRows(
    collectionAppId: string,
  ): Promise<DataObjectListItemV2[]> {
    const api = useV2ShepardApi(DataObjectV2Api);
    const rows: DataObjectListItemV2[] = [];
    for (let page = 0; page < MAX_PAGES; page++) {
      const batch = await api.value.listDataObjects({
        collectionAppId,
        fields: "id,appId,name",
        page,
        pageSize: PAGE_SIZE,
      });
      rows.push(...batch);
      if (batch.length < PAGE_SIZE) break;
    }
    return rows;
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
    entry.promise = fetchAllRows(collectionId)
      .then(rows => {
        entry.map.value = new Map(rows.map(d => [d.id, d.name]));
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
