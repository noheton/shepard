import { DataObjectApi, type ResponseError } from "@dlr-shepard/backend-client";
import {
  mapToTreeViewItems,
  type TreeViewItem,
} from "~/components/collection/collectionUtils";

export function useDataObjectMapByCollection(collectionId: number) {
  const dataObjectsMap = ref<Map<number, string>>(new Map<number, string>());

  function fetchDataObjectsOfCollection() {
    createApiInstance(DataObjectApi)
      .getAllDataObjects({ collectionId })
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

export function useDataObjectListByCollection(
  collectionId: number,
  parentId?: number,
) {
  const dataObjectsList = ref<TreeViewItem[] | undefined>(undefined);

  async function fetchDataObjectsOfCollection() {
    createApiInstance(DataObjectApi)
      .getAllDataObjects({ collectionId, parentId })
      .then(response => {
        dataObjectsList.value = mapToTreeViewItems(response);
      })
      .catch(error => {
        handleError(error, "getAllDataObjects");
      });
  }

  fetchDataObjectsOfCollection();

  return { dataObjectsList };
}
