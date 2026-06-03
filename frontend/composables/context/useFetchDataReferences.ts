import type {
  FileReference,
  ResponseError,
  StructuredDataReference,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";
import {
  FileContainerApi,
  FileReferenceApi,
  instanceOfFileReference,
  instanceOfTimeseriesReference,
  StructuredDataContainerApi,
  StructuredDataReferenceApi,
  TimeseriesContainerApi,
  TimeseriesReferenceApi,
} from "@dlr-shepard/backend-client";
import type {
  DataReference,
  DataReferenceWithoutContainerName,
  ReferencedContainerMeta,
} from "~/components/context/display-components/data-references/dataReference";
import { useShepardApi } from "../common/api/useShepardApi";

// BUG-COLL-APPID-ROUTE-007-PAGE: accept the numeric ids as a plain number, a
// Ref, or a getter and resolve them at fetch time. The DataObject detail page's
// route params are now the v2 appId (UUID), so the NUMERIC ids these v1
// `/shepard/api/...` endpoints require only become available once the loaded v2
// entities resolve. The composable defers its first fetch until both ids are
// present and re-fetches when they appear.
export function useDataReferencesByDataObject(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const dataReferences = ref<Array<DataReference> | undefined>(undefined);

  function ids(): { collectionId: number; dataObjectId: number } | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    if (collectionId == null || dataObjectId == null) return undefined;
    return { collectionId, dataObjectId };
  }

  async function fetchTimeseriesReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<TimeseriesReference[]> {
    return useShepardApi(TimeseriesReferenceApi)
      .value.getAllTimeseriesReferences({
        collectionId,
        dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchTimeseriesReferences");
        return [];
      });
  }

  async function fetchFileReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<FileReference[]> {
    return useShepardApi(FileReferenceApi)
      .value.getAllFileReferences({
        collectionId,
        dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchFileReferences");
        return [];
      });
  }

  async function fetchStructuredDataReferences(
    collectionId: number,
    dataObjectId: number,
  ): Promise<StructuredDataReference[]> {
    return useShepardApi(StructuredDataReferenceApi)
      .value.getAllStructuredDataReferences({
        collectionId,
        dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchStructuredDataReferences");
        return [];
      });
  }

  async function fetchTimeseriesContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return useShepardApi(TimeseriesContainerApi)
      .value.getTimeseriesContainer({ timeseriesContainerId: containerId })
      .then((response): ReferencedContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden" };
        handleError(error, "fetchTimeseriesContainerName");
        return { referencedContainerAvailability: "error" };
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

  async function fetchStructuredDataContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return useShepardApi(StructuredDataContainerApi)
      .value.getStructuredDataContainer({
        structuredDataContainerId: containerId,
      })
      .then((response): ReferencedContainerMeta => {
        return {
          referencedContainerName: response.name,
          referencedContainerAvailability: "available",
        };
      })
      .catch((error: ResponseError) => {
        if (error.response.status === 403)
          return { referencedContainerAvailability: "forbidden" };
        handleError(error, "fetchStructuredDataContainerName");
        return { referencedContainerAvailability: "error" };
      });
  }

  async function addContainerName(
    ref: DataReferenceWithoutContainerName,
  ): Promise<DataReference> {
    if (instanceOfTimeseriesReference(ref)) {
      return {
        ...ref,
        ...(await fetchTimeseriesContainerMeta(ref.timeseriesContainerId)),
      };
    }
    if (instanceOfFileReference(ref)) {
      return { ...ref, ...(await fetchFileContainerMeta(ref.fileContainerId)) };
    }
    return {
      ...ref,
      ...(await fetchStructuredDataContainerMeta(
        ref.structuredDataContainerId,
      )),
    };
  }

  async function fetchAndMergeReferences() {
    const resolved = ids();
    if (!resolved) return;
    const { collectionId, dataObjectId } = resolved;
    const [timeseriesReferences, fileReferences, structuredDataReferences] =
      await Promise.all([
        fetchTimeseriesReferences(collectionId, dataObjectId),
        fetchFileReferences(collectionId, dataObjectId),
        fetchStructuredDataReferences(collectionId, dataObjectId),
      ]);
    const references = [
      ...timeseriesReferences,
      ...fileReferences,
      ...structuredDataReferences,
    ];
    dataReferences.value = await Promise.all(references.map(addContainerName));
  }

  // Fetch once both ids are resolvable; re-fetch when they first appear (the
  // route-param-is-appId case where the numeric ids arrive after the v2 load).
  watch(ids, resolved => {
    if (resolved) fetchAndMergeReferences();
  }, { immediate: true });

  onDataObjectUpdated(fetchAndMergeReferences);

  return { dataReferences };
}
