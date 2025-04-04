import {
  FileReferenceApi,
  type ShepardFile,
} from "@dlr-shepard/backend-client";

export function useFetchFileReferencePayload(
  collectionId: number,
  dataObjectId: number,
  fileReferenceId: number,
) {
  const files = ref<ShepardFile[]>([]);

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

  fetchFiles();

  return { files };
}
