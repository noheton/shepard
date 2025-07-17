import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { TimeseriesContainerApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export async function useFetchTimeseriesAnnotations(
  timeseriesContainerId: number,
  timeseriesId: number,
): Promise<Array<SemanticAnnotation>> {
  const timeseries = await useShepardApi(TimeseriesContainerApi)
    .value.getAllAnnotationsOfTimeseries({
      timeseriesContainerId,
      timeseriesId,
    })
    .then(response => {
      return response;
    })
    .catch(() => {
      return [];
    });
  return timeseries;
}
