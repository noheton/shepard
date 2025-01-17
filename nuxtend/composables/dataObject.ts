import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";

// The DataObject domain model defines the description to be nullable.
// In the frontend we handle that here to be sure that it is an empty string
// instead of null or undefined.
// That simplifies life if we bind the description to the text editor component.
// If we decide to handle this within the component, we can remove this type.
export interface DataObjectSanitized extends DataObject {
  description: string;
}

export function useDataObject(collectionId: number, dataObjectId: number) {
  const dataObject = ref<DataObjectSanitized | undefined>(undefined);

  function fetchDataObject() {
    createApiInstance(DataObjectApi)
      .getDataObject({
        collectionId: collectionId,
        dataObjectId: dataObjectId,
      })
      .then(response => {
        dataObject.value = {
          ...response,
          description: response.description ?? "",
        };
      })
      .catch(error => {
        handleError(error, "getDataObject");
      });
  }

  fetchDataObject();

  return { dataObject };
}
