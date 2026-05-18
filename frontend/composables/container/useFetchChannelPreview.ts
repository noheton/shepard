import {
  AggregateFunction,
  TimeseriesContainerApi,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

const PREVIEW_BUCKET_NS = 1_000_000_000; // 1 second in nanoseconds

export function useFetchChannelPreview(
  containerId: number,
  channel: TimeseriesEntity,
) {
  const data = ref<Array<[number, number]>>([]);
  const loading = ref(false);

  async function fetch() {
    loading.value = true;
    try {
      const endNs = Date.now() * 1e6; // ms → ns (double precision fine here)
      const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
        timeseriesContainerId: containerId,
        measurement: channel.measurement ?? "",
        device: channel.device ?? "",
        location: channel.location ?? "",
        symbolicName: channel.symbolicName ?? "",
        field: channel.field ?? "",
        start: 0,
        end: endNs,
        _function: AggregateFunction.Mean,
        groupBy: Number(PREVIEW_BUCKET_NS),
      });
      data.value = (result.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
    } catch {
      data.value = [];
    } finally {
      loading.value = false;
    }
  }

  fetch();

  return { data, loading };
}
