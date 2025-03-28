import type {
  ResponseError,
  TimeseriesReference,
} from "@dlr-shepard/backend-client";
import { TimeseriesReferenceApi } from "@dlr-shepard/backend-client";

export function useFetchTimeseriesReference(
  collectionId: number,
  dataObjectId: number,
  timeseriesId: number,
) {
  const timeseriesReference = ref<TimeseriesReference | undefined>(undefined);

  function fetchTimeseriesReference(
    collectionId: number,
    dataObjectId: number,
    timeseriesReferenceId: number,
  ) {
    createApiInstance(TimeseriesReferenceApi)
      .getTimeseriesReference({
        collectionId,
        dataObjectId,
        timeseriesReferenceId,
      })
      .then(response => {
        timeseriesReference.value = response;
      })
      .catch(e => {
        timeseriesReference.value = undefined;
        handleError(e as ResponseError, "fetching timeseriesReference");
      });
  }

  fetchTimeseriesReference(collectionId, dataObjectId, timeseriesId);

  return {
    timeseriesReference,
    fetchTimeseriesReference,
  };
}
