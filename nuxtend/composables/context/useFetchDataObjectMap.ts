import { DataObjectApi, type ResponseError } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchDataObjectMapByCollection(collectionId: number) {
  const dataObjectsMap = ref<Map<number, string>>(new Map<number, string>());

  function fetchDataObjectsOfCollection() {
    useShepardApi(DataObjectApi)
      .value.getAllDataObjects({ collectionId })
      .then(response => {
        response.forEach(dataObject => {
          dataObjectsMap.value.set(dataObject.id, dataObject.name);
        });
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching dataobjects");
      });
  }

  fetchDataObjectsOfCollection();

  return { dataObjectsMap };
}
