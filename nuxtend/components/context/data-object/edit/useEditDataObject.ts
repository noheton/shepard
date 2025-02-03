import { DataObjectApi } from "@dlr-shepard/backend-client";
import type { UpdatedDataObject } from "./updatedDataObject";

export function useEditDataObject(
  collectionId: number,
  dataObjectId: number,
  isValid: Ref<boolean>,
  onSuccess: () => void,
) {
  const { dataObject } = useFetchDataObject(collectionId, dataObjectId);

  const updatedDataObject = ref<UpdatedDataObject | undefined>(undefined);

  const loading = computed(() => !dataObject && !updatedDataObject);

  function updateDataObject(newValue: UpdatedDataObject) {
    updatedDataObject.value = newValue;
  }

  async function saveChanges() {
    const dataObjectToSave = updatedDataObject.value;
    if (dataObjectToSave === undefined) return;
    if (isValid.value === false) return;

    createApiInstance(DataObjectApi)
      .updateDataObject({
        collectionId: collectionId,
        dataObjectId: dataObjectId,
        dataObject: {
          ...dataObjectToSave,
          predecessorIds: uniqueNumbersOf(
            // clean up possible remaining placeholder entries
            dataObjectToSave.predecessorIds?.filter(entry => entry !== -1) ??
              [],
          ),
        },
      })
      .then(_ => {
        emitSuccess(
          `Successfully updated data object ${dataObjectToSave.name}`,
        );
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "updateDataObject");
      });
  }

  watch(dataObject, newDo => {
    if (newDo) {
      updateDataObject({
        name: newDo.name,
        parentId: newDo.parentId,
        attributes: newDo.attributes,
        description: newDo.description,
        predecessorIds: newDo.predecessorIds,
      });
    }
  });

  return {
    saveChanges,
    updatedDataObject,
    updateDataObject,
    loading,
  };
}
