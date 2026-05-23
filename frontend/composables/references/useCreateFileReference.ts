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
    // Guard against the empty-oids case before the request fires.
    // Backend rejects with 400 "fileOids must not be empty" otherwise; the
    // user previously saw the truncated "Error while createFileReference:"
    // toast because the rejection body wasn't surfaced.
    if (!fileRef.fileOids || fileRef.fileOids.length === 0) {
      handleError(
        new Error(
          "No files to link — upload at least one file before creating the reference.",
        ),
        "createFileReference",
      );
      return;
    }

    loading.value = true;
    try {
      await useShepardApi(FileReferenceApi).value.createFileReference({
        collectionId,
        dataObjectId,
        fileReference: {
          ...fileRef,
          name,
          fileContainerId,
        },
      });
      emitSuccess("Successfully created file reference");
      handleDataObjectUpdate();
      onSuccess();
    } catch (error) {
      handleError(error, "createFileReference");
    } finally {
      loading.value = false;
    }
  }

  return {
    addFileReference,
    loading,
  };
}
