import { DataObjectApi, DataObjectV2Api, type DataObjectListItemV2 } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

/**
 * Fetches all DataObjects for a Collection.
 *
 * When `collectionAppId` is provided the v2 endpoint is used, which includes
 * per-kind reference counts (`timeseriesCount`, `fileCount`, `structuredDataCount`).
 * Falls back to the v1 endpoint (numeric collectionId only) when appId is not yet
 * available (e.g. before the Collection has loaded).
 */
export function useFetchAllDataObjects(collectionId: number, collectionAppId?: Ref<string | null>) {
  const dataObjects = ref<DataObjectListItemV2[]>([]);
  const loading = ref(false);

  const v2Api = useV2ShepardApi(DataObjectV2Api);
  const v1Api = useShepardApi(DataObjectApi);

  async function fetchAll() {
    const appId = collectionAppId?.value ?? null;
    loading.value = true;
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
        dataObjects.value = results;
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
        dataObjects.value = results;
      }
    } catch (e) {
      handleError(e, "fetchAllDataObjects");
      dataObjects.value = [];
    } finally {
      loading.value = false;
    }
  }

  if (collectionAppId) {
    watch(collectionAppId, (appId) => {
      if (appId !== null) void fetchAll();
    }, { immediate: true });
  } else {
    fetchAll();
  }

  return { dataObjects, loading };
}
