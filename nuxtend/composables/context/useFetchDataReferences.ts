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

export function useDataReferencesByDataObject(
  collectionId: number,
  dataObjectId: number,
) {
  const dataReferences = ref<Array<DataReference> | undefined>(undefined);

  async function fetchTimeseriesReferences(): Promise<TimeseriesReference[]> {
    return createApiInstance(TimeseriesReferenceApi)
      .getAllTimeseriesReferences({
        collectionId,
        dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchTimeseriesReferences");
        return [];
      });
  }

  async function fetchFileReferences(): Promise<FileReference[]> {
    return createApiInstance(FileReferenceApi)
      .getAllFileReferences({
        collectionId,
        dataObjectId,
      })
      .catch(error => {
        handleError(error, "fetchFileReferences");
        return [];
      });
  }

  async function fetchStructuredDataReferences(): Promise<
    StructuredDataReference[]
  > {
    return createApiInstance(StructuredDataReferenceApi)
      .getAllStructuredDataReferences({
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
    return createApiInstance(TimeseriesContainerApi)
      .getTimeseriesContainer({ timeseriesContainerId: containerId })
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

  async function fetchStructuredDataContainerMeta(
    containerId: number,
  ): Promise<ReferencedContainerMeta> {
    if (isDeleted(containerId))
      return { referencedContainerAvailability: "deleted" };
    return createApiInstance(StructuredDataContainerApi)
      .getStructuredDataContainer({ structuredDataContainerId: containerId })
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
    const [timeseriesReferences, fileReferences, structuredDataReferences] =
      await Promise.all([
        fetchTimeseriesReferences(),
        fetchFileReferences(),
        fetchStructuredDataReferences(),
      ]);
    const references = [
      ...timeseriesReferences,
      ...fileReferences,
      ...structuredDataReferences,
    ];
    dataReferences.value = await Promise.all(references.map(addContainerName));
  }

  fetchAndMergeReferences();

  onDataObjectUpdated(fetchAndMergeReferences);

  return { dataReferences };
}
