import {
  TimeseriesReferenceApi,
  type TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchTimeseriesPayload(
  collectionId: number,
  dataObjectId: number,
  timeseriesReferenceId: number,
) {
  const timeseriesWithDataPoints = ref<TimeseriesWithDataPoints[]>();
  const isLoading = ref<boolean>(false);

  function fetchTimeseriesPayload(
    collectionId: number,
    dataObjectId: number,
    timeseriesReferenceId: number,
  ) {
    isLoading.value = true;
    useShepardApi(TimeseriesReferenceApi)
      .value.getTimeseriesPayload({
        collectionId: collectionId,
        dataObjectId: dataObjectId,
        timeseriesReferenceId: timeseriesReferenceId,
      })
      .then(response => {
        timeseriesWithDataPoints.value = response;
      })
      .catch(error => {
        handleError(error, "getTimeseriesPayload");
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  fetchTimeseriesPayload(collectionId, dataObjectId, timeseriesReferenceId);

  return {
    timeseriesWithDataPoints,
    isLoading,
  };
}
