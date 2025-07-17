import type {
  ResponseError,
  TimeseriesContainer,
} from "@dlr-shepard/backend-client";
import { TimeseriesContainerApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchTimeSeriesContainer(timeseriesContainerId: number) {
  const timeseriesContainer = ref<TimeseriesContainer | undefined>(undefined);

  function fetchTimeseriesContainer(timeseriesContainerId: number) {
    useShepardApi(TimeseriesContainerApi)
      .value.getTimeseriesContainer({ timeseriesContainerId })
      .then(response => {
        timeseriesContainer.value = response;
      })
      .catch(e => {
        timeseriesContainer.value = undefined;
        handleError(e as ResponseError, "fetching timeseriesContainer");
      });
  }

  fetchTimeseriesContainer(timeseriesContainerId);

  return { timeseriesContainer, fetchTimeseriesContainer };
}
