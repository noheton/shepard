import {
  TimeseriesContainerApi,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * Channel preview composable.
 *
 * Returns a chart-ready, LTTB-downsampled view of the channel's full history
 * by default — the server picks the shape-preserving samples so peaks and
 * troughs survive even at heavy compression ratios (the LUMEN TR-004
 * vibration anomaly stays visible at 100-point preview). Set
 * {@code downsample = false} to receive raw points instead.
 *
 * The {@code downsampled} ref reports what actually came back so the UI
 * can show a "Downsampled" badge + toggle.
 */
export function useFetchChannelPreview(
  containerId: number,
  channel: TimeseriesEntity,
  options?: { downsample?: boolean; maxPoints?: number },
) {
  const downsampleOpt = options?.downsample ?? true;
  const maxPoints = options?.maxPoints ?? 2000;

  const data = ref<Array<[number, number]>>([]);
  const loading = ref(false);
  const downsampled = ref(false);

  async function fetch(downsample: boolean = downsampleOpt) {
    loading.value = true;
    try {
      const endNs = Date.now() * 1e6; // ms → ns
      const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
        timeseriesContainerId: containerId,
        measurement: channel.measurement ?? "",
        device: channel.device ?? "",
        location: channel.location ?? "",
        symbolicName: channel.symbolicName ?? "",
        field: channel.field ?? "",
        start: 0,
        end: endNs,
        ...(downsample
          ? { downsample: "lttb", maxPoints }
          : {}),
      });
      data.value = (result.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
      downsampled.value = downsample;
    } catch {
      data.value = [];
      downsampled.value = false;
    } finally {
      loading.value = false;
    }
  }

  fetch();

  return { data, loading, downsampled, refetch: fetch };
}
