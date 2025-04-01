import {
  TimeseriesReferenceApi,
  type TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";

export function useFetchTimeseriesPayload(
  collectionId: number,
  dataObjectId: number,
  timeseriesReferenceId: number,
) {
  const timeseriesWithDataPoints = ref<TimeseriesWithDataPoints[]>();
  const isLoading = ref<boolean>(true);

  function fetchTimeseriesPayload(
    collectionId: number,
    dataObjectId: number,
    timeseriesReferenceId: number,
  ) {
    createApiInstance(TimeseriesReferenceApi)
      .getTimeseriesPayload({
        collectionId: collectionId,
        dataObjectId: dataObjectId,
        timeseriesReferenceId: timeseriesReferenceId,
      })
      .then(response => {
        timeseriesWithDataPoints.value = response;
      })
      .catch(error => {
        handleError(error, "getTimeseriesPayload");
      });
  }

  fetchTimeseriesPayload(collectionId, dataObjectId, timeseriesReferenceId);

  return {
    timeseriesWithDataPoints,
    isLoading,
  };
}
