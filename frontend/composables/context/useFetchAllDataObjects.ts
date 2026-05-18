import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchAllDataObjects(collectionId: number) {
  const dataObjects = ref<DataObject[]>([]);
  const loading = ref(false);

  async function fetchAll() {
    loading.value = true;
    try {
      const PAGE = 200;
      let page = 0;
      const results: DataObject[] = [];
      while (true) {
        const batch = await useShepardApi(DataObjectApi).value.getAllDataObjects({
          collectionId,
          page,
          size: PAGE,
        });
        results.push(...batch);
        if (batch.length < PAGE) break;
        page++;
      }
      dataObjects.value = results;
    } catch (e) {
      handleError(e, "fetchAllDataObjects");
      dataObjects.value = [];
    } finally {
      loading.value = false;
    }
  }

  fetchAll();

  return { dataObjects, loading };
}
