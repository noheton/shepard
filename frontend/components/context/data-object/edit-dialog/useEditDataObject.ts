import { DataObjectApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { UpdatedDataObject } from "./updatedDataObject";

export function useEditDataObject(
  collectionId: number,
  dataObjectId: number,
  isValid: Ref<boolean>,
  onSuccess: () => void,
) {
  const updatedDataObject = ref<UpdatedDataObject | undefined>(undefined);

  // BUG-COLL-APPID-ROUTE-002: useFetchDataObject now takes string ids and
  // hits v2. The numeric id stringifies cleanly for both UUID v7 (already
  // string-shaped) and legacy long form callers; the v2 EntityIdResolver
  // accepts either shape.
  const { dataObject } = useFetchDataObject(
    String(collectionId),
    String(dataObjectId),
  );
  const loading = computed(() => !dataObject && !updatedDataObject);
  watch(dataObject, newDo => {
    if (newDo) {
      // LIC1: read license / accessRights defensively — the generated client
      // model may not yet expose them as top-level fields.
      const rawDo = newDo as unknown as {
        license?: string | null;
        accessRights?: string | null;
      };
      updatedDataObject.value = {
        name: newDo.name,
        parentId: newDo.parentId,
        attributes: newDo.attributes ?? {},
        description: newDo.description,
        predecessorIds: newDo.predecessorIds ?? [],
        status: newDo.status ?? null,
        license: rawDo.license ?? null,
        accessRights: rawDo.accessRights ?? null,
      };
    }
  });

  async function saveChanges() {
    const dataObjectToSave = updatedDataObject.value;
    if (dataObjectToSave === undefined) return;
    if (isValid.value === false) return;

    useShepardApi(DataObjectApi)
      .value.updateDataObject({
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
          `Successfully updated data object "${dataObjectToSave.name}"`,
        );
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "updateDataObject");
      });
  }

  return {
    updatedDataObject,
    loading,
    saveChanges,
  };
}
