import {
  FileReferenceApi,
  type FileReference,
  type ShepardFile,
} from "@dlr-shepard/backend-client";

export function useFetchFileReference(
  collectionId: number,
  dataObjectId: number,
  fileReferenceId: number,
) {
  const fileReference = ref<FileReference | undefined>(undefined);
  const files = ref<ShepardFile[]>([]);

  function fetchFileReference() {
    createApiInstance(FileReferenceApi)
      .getFileReference({
        collectionId,
        dataObjectId,
        fileReferenceId,
      })
      .then(response => {
        fileReference.value = response;
        return response;
      })
      .catch(error => {
        fileReference.value = undefined;
        handleError(error, "getFileReference");
      });
  }

  function fetchFiles() {
    createApiInstance(FileReferenceApi)
      .getFiles({
        collectionId,
        dataObjectId,
        fileReferenceId,
      })
      .then(response => {
        files.value = response;
      })
      .catch(error => {
        handleError(error, "getFiles");
      });
  }

  watch(fileReference, () => {
    if (fileReference.value) {
      fetchFiles();
    }
  });

  fetchFileReference();

  return { fileReference, files };
}
