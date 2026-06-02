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
import { useShepardApi } from "../common/api/useShepardApi";

/** PV1a-UI: Extended container meta carrying the UUID v7 appId for v2 endpoints. */
type FileContainerMeta = ReferencedContainerMeta & {
  /** UUID v7 of the FileContainer; null for pre-L2a containers without an appId. */
  fileContainerAppId?: string | null;
};

export function useFetchFileReference(
  collectionId: number,
  dataObjectId: number,
  fileReferenceId: number,
) {
  const fileReference = ref<FileReferenceWithContainerMeta | undefined>(
    undefined,
  );
  const files = ref<FileMeta[]>([]);
  /** UUID v7 of the referenced FileContainer; null when the container pre-dates L2a. */
  const fileContainerAppId = ref<string | null>(null);

  async function fetchFileReference() {
    useShepardApi(FileReferenceApi)
      .value.getFileReference({
        collectionId,
        dataObjectId,
        fileReferenceId,
      })
      .then(async response => {
        const containerMeta = await fetchFileContainerMeta(
          response.fileContainerId,
        );
        fileContainerAppId.value = containerMeta.fileContainerAppId ?? null;
        fileReference.value = {
          ...response,
          ...containerMeta,
        };
      })
      .catch(error => {
        handleError(error, "getFileReference");
        fileReference.value = undefined;
      });
  }

  async function fetchFileContainerMeta(
    containerId: number,
  ): Promise<FileContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted", fileContainerAppId: null };
    return useShepardApi(FileContainerApi)
      .value.getFileContainer({ fileContainerId: containerId })
      .then((response): FileContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
          fileContainerAppId: response.appId ?? null,
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden", fileContainerAppId: null };
        handleError(error, "fetchFileContainerName");
        return { referencedContainerAvailability: "error", fileContainerAppId: null };
      });
  }

  async function fetchReferencedFiles(): Promise<ShepardFile[]> {
    return useShepardApi(FileReferenceApi)
      .value.getFiles({
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
    return useShepardApi(FileContainerApi)
      .value.getAllFiles({ fileContainerId })
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

  return { fileReference, files, fileContainerAppId };
}
