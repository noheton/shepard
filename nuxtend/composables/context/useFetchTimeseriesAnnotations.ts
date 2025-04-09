import type { SemanticAnnotation } from "@dlr-shepard/backend-client";
import { TimeseriesContainerApi } from "@dlr-shepard/backend-client";

export async function useFetchTimeseriesAnnotations(
  timeseriesContainerId: number,
  timeseriesId: number,
): Promise<Array<SemanticAnnotation>> {
  const timeseries = await createApiInstance(TimeseriesContainerApi)
    .getAllAnnotationsOfTimeseries({
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
