import {
  TimeseriesReferenceApi,
  type TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

// BUG-COLL-APPID-ROUTE-007-REFPAGE: accept the numeric ids as a plain number, a
// Ref, or a getter and resolve them at fetch time, mirroring the pattern used
// in useFetchTimeseriesReferences.ts and useFetchDataReferences.ts.
export function useFetchTimeseriesPayload(
  collectionIdInput: MaybeRefOrGetter<number | undefined>,
  dataObjectIdInput: MaybeRefOrGetter<number | undefined>,
  timeseriesReferenceIdInput: MaybeRefOrGetter<number | undefined>,
) {
  const timeseriesWithDataPoints = ref<TimeseriesWithDataPoints[]>();
  const isLoading = ref<boolean>(false);

  function ids():
    | { collectionId: number; dataObjectId: number; timeseriesReferenceId: number }
    | undefined {
    const collectionId = toValue(collectionIdInput);
    const dataObjectId = toValue(dataObjectIdInput);
    const timeseriesReferenceId = toValue(timeseriesReferenceIdInput);
    if (collectionId == null || dataObjectId == null || timeseriesReferenceId == null)
      return undefined;
    return { collectionId, dataObjectId, timeseriesReferenceId };
  }

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

  // Fetch once all ids are resolvable; re-fetch when they first appear (the
  // route-param-is-appId case where the numeric ids arrive after the v2 load).
  watch(ids, resolved => {
    if (resolved)
      fetchTimeseriesPayload(
        resolved.collectionId,
        resolved.dataObjectId,
        resolved.timeseriesReferenceId,
      );
  }, { immediate: true });

  return {
    timeseriesWithDataPoints,
    isLoading,
  };
}
