import {
  FileReferenceApi,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchFileReferencePayload(
  collectionId: number,
  dataObjectId: number,
  fileReferenceId: number,
) {
  const files = ref<ShepardFile[]>([]);

  function fetchFiles() {
    useShepardApi(FileReferenceApi)
      .value.getFiles({
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
