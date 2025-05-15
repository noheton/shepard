import type { ResponseError } from "@dlr-shepard/backend-client";
import { TimeseriesReferenceApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export type Metrics = {
  MIN: string;
  MAX: string;
  STDDEV: string;
  COUNT: string;
  MEAN: string;
  MEDIAN: string;
  FIRST: string;
  LAST: string;
  FREQUENCY: string;
};

export async function useFetchTimeseriesReferenceMetrics(
  collectionId: number,
  dataObjectId: number,
  timeseriesReferenceId: number,
  measurement: string,
  device: string,
  location: string,
  symbolicName: string,
  field: string,
): Promise<Metrics | undefined> {
  const metrics = await useShepardApi(TimeseriesReferenceApi)
    .value.getMetricsOfTimeseriesReference({
      collectionId,
      dataObjectId,
      timeseriesReferenceId,
      measurement,
      device,
      location,
      symbolicName,
      field,
    })
    .then(response => {
      return response.reduce((acc, metric) => {
        const key = metric._function;
        acc[key as keyof Metrics] = toFormattedDouble(metric.value, 2);
        return acc;
      }, {} as Metrics);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseriesReference metrics");
      return undefined;
    });
  return metrics;
}
