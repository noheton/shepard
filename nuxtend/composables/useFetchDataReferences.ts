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
} from "~/components/context/data-object/data/dataReference";

export function useDataReferencesByDataObject(
  collectionId: number,
  dataObjectId: number,
) {
  const dataReferences = ref<Array<DataReference> | undefined>(undefined);

  async function fetchTimeseriesReferences() {
    return createApiInstance(TimeseriesReferenceApi)
      .getAllTimeseriesReferences({
        collectionId,
        dataObjectId,
      })
      .then(response => {
        return response;
      })
      .catch(error => {
        handleError(error, "getAllTimeseriesReferences");
      });
  }

  async function fetchFileReferences() {
    return createApiInstance(FileReferenceApi)
      .getAllFileReferences({
        collectionId,
        dataObjectId,
      })
      .then(response => {
        return response;
      })
      .catch(error => {
        handleError(error, "getAllFileReferences");
      });
  }

  async function fetchStructuredDataReferences() {
    return createApiInstance(StructuredDataReferenceApi)
      .getAllStructuredDataReferences({
        collectionId,
        dataObjectId,
      })
      .then(response => {
        return response;
      })
      .catch(error => {
        handleError(error, "getAllStructuredDataReferences");
      });
  }

  async function fetchTimeseriesContainerName(containerId: number) {
    return createApiInstance(TimeseriesContainerApi)
      .getTimeseriesContainer({ timeseriesContainerId: containerId })
      .then(response => {
        return response.name;
      })
      .catch(error => {
        handleError(error, "fetchTimeseriesContainerName");
      });
  }

  async function fetchFileContainerName(containerId: number) {
    return createApiInstance(FileContainerApi)
      .getFileContainer({ fileContainerId: containerId })
      .then(response => {
        return response.name;
      })
      .catch(error => {
        handleError(error, "fetchTimeseriesContainerName");
      });
  }

  async function fetchStructuredDataContainerName(containerId: number) {
    return createApiInstance(StructuredDataContainerApi)
      .getStructuredDataContainer({ structuredDataContainerId: containerId })
      .then(response => {
        return response.name;
      })
      .catch(error => {
        handleError(error, "fetchTimeseriesContainerName");
      });
  }

  async function addContainerName(
    ref: DataReferenceWithoutContainerName,
  ): Promise<DataReference> {
    if (instanceOfTimeseriesReference(ref))
      return {
        ...ref,
        referencedContainerName:
          (await fetchTimeseriesContainerName(ref.timeseriesContainerId)) ?? "",
      };
    if (instanceOfFileReference(ref))
      return {
        ...ref,
        referencedContainerName:
          (await fetchFileContainerName(ref.fileContainerId)) ?? "",
      };
    return {
      ...ref,
      referencedContainerName:
        (await fetchStructuredDataContainerName(
          ref.structuredDataContainerId,
        )) ?? "",
    };
  }

  async function fetchAndMergeReferences() {
    const [timeseriesReferences, fileReferences, structuredDataReferences] =
      await Promise.all([
        fetchTimeseriesReferences(),
        fetchFileReferences(),
        fetchStructuredDataReferences(),
      ]);
    if (!timeseriesReferences || !fileReferences || !structuredDataReferences) {
      return;
    }
    const references = [
      ...timeseriesReferences,
      ...fileReferences,
      ...structuredDataReferences,
    ];
    dataReferences.value = await Promise.all(references.map(addContainerName));
  }

  fetchAndMergeReferences();

  return { dataReferences };
}
