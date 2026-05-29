import { DataObjectApi, type ResponseError } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * Fetches a map of DataObject id → name for the given collection.
 *
 * PERF11: the initial load goes through useAsyncData so the first render
 * is SSR-serialised. useAsyncData must be called unconditionally at the
 * composable root (not inside if-branches), so the async function body
 * performs the actual fetch and returns the raw array; the reactive map
 * is derived in a computed from the data ref.
 */
export function useFetchDataObjectMapByCollection(collectionId: number) {
  const dataObjectApi = useShepardApi(DataObjectApi);

  const { data } = useAsyncData(
    `dataobject-map-${collectionId}`,
    async () => {
      try {
        return await dataObjectApi.value.getAllDataObjects({ collectionId });
      } catch (e) {
        handleError(e as ResponseError, "fetching dataobjects");
        return [];
      }
    },
    { default: () => [] },
  );

  const dataObjectsMap = computed<Map<number, string>>(() => {
    const map = new Map<number, string>();
    for (const obj of data.value ?? []) {
      if (obj.id != null) map.set(obj.id, obj.name);
    }
    return map;
  });

  return { dataObjectsMap };
}
