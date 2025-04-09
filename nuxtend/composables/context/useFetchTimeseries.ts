import type { ResponseError } from "@dlr-shepard/backend-client";
import { TimeseriesContainerApi } from "@dlr-shepard/backend-client";

export async function useFetchTimeseries(
  timeseriesContainerId: number,
  measurement: string,
  device: string,
  location: string,
  symbolicName: string,
  field: string,
) {
  const timeseries = await createApiInstance(TimeseriesContainerApi)
    .getTimeseriesOfContainer({
      timeseriesContainerId,
      measurement,
      device,
      location,
      symbolicName,
      field,
    })
    .then(response => {
      return response[0];
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries");
      return undefined;
    });
  return timeseries;
}
