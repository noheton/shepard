import { FileReferenceApi } from "@dlr-shepard/backend-client";
import type { FileRef } from "~/components/context/data-references/create-dialog/DataRef";
import { useShepardApi } from "../common/api/useShepardApi";

export function useCreateFileReference(
  collectionId: number,
  dataObjectId: number,
  onSuccess: () => void,
  isLoading?: Ref<boolean>,
) {
  const loading = isLoading ?? ref<boolean>(false);

  async function addFileReference(
    name: string,
    fileContainerId: number,
    fileRef: FileRef,
  ) {
    loading.value = true;

    useShepardApi(FileReferenceApi)
      .value.createFileReference({
        collectionId,
        dataObjectId,
        fileReference: {
          ...fileRef,
          name,
          fileContainerId,
        },
      })
      .then(_ => {
        emitSuccess("Successfully created file reference");
        handleDataObjectUpdate();
        onSuccess();
      })
      .catch(error => {
        handleError(error, "createFileReference");
      });
    loading.value = false;
  }

  return {
    addFileReference,
    loading,
  };
}
