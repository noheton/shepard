import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";

export function useUpdateDataObjectPredecessor(
  collectionId: number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function addPredecessor(
    dataobjectId: number,
    newPredecessorDataObjectId: number,
  ) {
    loading.value = true;

    let dataObject: DataObject | undefined = undefined;
    try {
      dataObject = await createApiInstance(DataObjectApi).getDataObject({
        collectionId: collectionId,
        dataObjectId: dataobjectId,
      });
    } catch (error) {
      dataObject = undefined;
      handleError(error, "getDataObject");
    }

    if (dataObject == undefined) {
      loading.value = false;
      return;
    }

    if (!dataObject.predecessorIds) {
      dataObject.predecessorIds = [];
    }

    if (dataObject.predecessorIds.includes(newPredecessorDataObjectId)) {
      handleError(
        `There already is a relationship between Data Objects with Id ${dataobjectId} and ${newPredecessorDataObjectId}`,
        "updateDataObject",
      );
      loading.value = false;
      return;
    }

    dataObject.predecessorIds.push(newPredecessorDataObjectId);

    createApiInstance(DataObjectApi)
      .updateDataObject({
        collectionId: collectionId,
        dataObjectId: dataobjectId,
        dataObject: {
          ...dataObject,
          predecessorIds: uniqueNumbersOf(
            // clean up possible remaining placeholder entries
            dataObject.predecessorIds.filter(entry => entry !== -1) ?? [],
          ),
        },
      })
      .then(_ => {
        emitSuccess("Successfully updated data object");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "updateDataObject");
      });
    loading.value = false;
  }

  /**
   * Removes a single Data Object id from the predecessor list of an dataobject.
   *
   * @param dataobjectId id of the dataobject the relationship is going out
   * @param toBeDeletedPredecessorId id of the referenced data object
   */
  async function deletePredecessor(
    dataobjectId: number,
    toBeDeletedPredecessorId: number,
  ) {
    loading.value = true;

    let dataObject: DataObject | undefined = undefined;
    try {
      dataObject = await createApiInstance(DataObjectApi).getDataObject({
        collectionId: collectionId,
        dataObjectId: dataobjectId,
      });
    } catch (error) {
      dataObject = undefined;
      handleError(error, "getDataObject");
    }

    if (dataObject == undefined) {
      loading.value = false;
      return;
    }

    if (!dataObject.predecessorIds || dataObject.predecessorIds.length === 0) {
      handleError(
        `Cannot remove predecessor. DataObject with Id ${dataobjectId} does not have any set predecessors.`,
        "updateDataObject",
      );
      loading.value = false;
      return;
    }

    const updatedPredecessorList = dataObject.predecessorIds.filter(
      id => id !== toBeDeletedPredecessorId,
    );

    createApiInstance(DataObjectApi)
      .updateDataObject({
        collectionId: collectionId,
        dataObjectId: dataobjectId,
        dataObject: {
          ...dataObject,
          predecessorIds: uniqueNumbersOf(
            // clean up possible remaining placeholder entries
            updatedPredecessorList.filter(entry => entry !== -1) ?? [],
          ),
        },
      })
      .then(_ => {
        emitSuccess("Successfully updated data object");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "updateDataObject");
      });
    loading.value = false;
  }

  return {
    addPredecessor,
    deletePredecessor,
    loading,
  };
}
