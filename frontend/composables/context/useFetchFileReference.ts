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

// BUG-COLL-APPID-ROUTE-007-REFPAGE: accept the numeric ids as a plain number, a
// Ref, or a getter and resolve them at fetch time. The reference detail page's
// route params are now the v2 appId (UUID), so the NUMERIC ids these v1
// `/shepard/api/...` endpoints require only become available once the loaded v2
// entities resolve. The composable defers its first fetch until all three ids
// are present and re-fetches when they appear.
export function useFetchFileReference(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
  fileReferenceIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const fileReference = ref<FileReferenceWithContainerMeta | undefined>(
    undefined,
  );
  const files = ref<FileMeta[]>([]);
  // UU1 — UI-404-NICE-EMPTY-STATE: 404 → render `EntityNotFound`, not a toast.
  const notFound = ref<boolean>(false);

  function ids():
    | { collectionId: number; dataObjectId: number; fileReferenceId: number }
    | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    const fileReferenceId = toValue(fileReferenceIdInput);
    if (collectionId == null || dataObjectId == null || fileReferenceId == null)
      return undefined;
    return { collectionId, dataObjectId, fileReferenceId };
  }

  async function fetchFileReference(
    collectionId: number,
    dataObjectId: number,
    fileReferenceId: number,
  ) {
    notFound.value = false;
    useShepardApi(FileReferenceApi)
      .value.getFileReference({
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
        fileReference.value = undefined;
        if ((error as ResponseError)?.response?.status === 404) {
          notFound.value = true;
          return;
        }
        handleError(error, "getFileReference");
      });
  }

  async function fetchFileContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return useShepardApi(FileContainerApi)
      .value.getFileContainer({ fileContainerId: containerId })
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

  async function fetchReferencedFiles(
    collectionId: number,
    dataObjectId: number,
    fileReferenceId: number,
  ): Promise<ShepardFile[]> {
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
    const resolved = ids();
    if (
      resolved &&
      fileReference.value &&
      !isDeleted(fileReference.value.fileContainerId) &&
      fileReference.value.referencedContainerAvailability === "available"
    ) {
      const [referencedFiles, existingFiles] = await Promise.all([
        fetchReferencedFiles(
          resolved.collectionId,
          resolved.dataObjectId,
          resolved.fileReferenceId,
        ),
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

  // Fetch once all ids are resolvable; re-fetch when they first appear (the
  // route-param-is-appId case where the numeric ids arrive after the v2 load).
  watch(ids, resolved => {
    if (resolved)
      fetchFileReference(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.fileReferenceId,
      );
  }, { immediate: true });

  return { fileReference, files, notFound };
}
