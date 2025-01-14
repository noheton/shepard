import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";

export function useDataObject(collectionId: number, dataObjectId: number) {
  const dataObject = ref<DataObject | undefined>(undefined);

  function fetchDataObject() {
    createApiInstance(DataObjectApi)
      .getDataObject({
        collectionId: collectionId,
        dataObjectId: dataObjectId,
      })
      .then(response => {
        dataObject.value = response;
      })
      .catch(error => {
        handleError(error, "getDataObject");
      });
  }

  fetchDataObject();

  return { dataObject };
}
