import {
  FileContainerApi,
  FileReferenceApi,
  type ResponseError,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import type { ReferencedContainerMeta } from "~/components/context/display-components/data-references/dataReference";
import type {
  FileMeta,
  FileReferenceWithContainerMeta,
} from "~/components/context/display-components/file-references/fileReferenceTypes";

export function useFetchFileReference(
  collectionId: number,
  dataObjectId: number,
  fileReferenceId: number,
) {
  const fileReference = ref<FileReferenceWithContainerMeta | undefined>(
    undefined,
  );
  const files = ref<FileMeta[]>([]);

  async function fetchFileReference() {
    createApiInstance(FileReferenceApi)
      .getFileReference({
        collectionId,
        dataObjectId,
        fileReferenceId,
      })
      .then(async response => {
        const fileRefMeta = await fetchFileContainerMeta(
          response.fileContainerId,
        );
        fileReference.value = {
          ...response,
          ...fileRefMeta,
        };
      })
      .catch(error => {
        handleError(error, "getFileReference");
        fileReference.value = undefined;
      });
  }

  async function fetchFileContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return createApiInstance(FileContainerApi)
      .getFileContainer({ fileContainerId: containerId })
      .then((response): ReferencedContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden" };
        handleError(error, "fetchFileContainerName");
        return { referencedContainerAvailability: "error" };
      });
  }

  async function fetchReferencedFiles(): Promise<ShepardFile[]> {
    return createApiInstance(FileReferenceApi)
      .getFiles({
        collectionId,
        dataObjectId,
        fileReferenceId,
      })
      .catch(error => {
        handleError(error, "getFiles");
        return [];
      });
  }

  async function fetchExistingFilesInContainer(
    fileContainerId: number,
  ): Promise<ShepardFile[]> {
    return createApiInstance(FileContainerApi)
      .getAllFiles({ fileContainerId })
      .catch(error => {
        handleError(error, "getFiles");
        return [];
      });
  }

  watch(fileReference, async () => {
    if (
      fileReference.value &&
      !isDeleted(fileReference.value.fileContainerId) &&
      fileReference.value.referencedContainerAvailability === "available"
    ) {
      const [referencedFiles, existingFiles] = await Promise.all([
        fetchReferencedFiles(),
        fetchExistingFilesInContainer(fileReference.value.fileContainerId),
      ]);

      const existingFileOids = existingFiles.map(file => file.oid);
      const referencedFilesWithMeta = referencedFiles.map(file => {
        return {
          ...file,
          availability: existingFileOids.includes(file.oid)
            ? ("available" as FileMeta["availability"])
            : ("deleted" as FileMeta["availability"]),
        };
      });
      files.value = referencedFilesWithMeta;
    }
  });

  fetchFileReference();

  return { fileReference, files };
}
