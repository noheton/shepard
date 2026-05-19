/**
 * TS_STATS1 — fetches storage + ingest stats for a TimeseriesContainer.
 * Calls GET /v2/timeseries-containers/{containerId}/stats.
 */
import { v2BaseUrl } from "~/composables/common/api/useV2ShepardApi";

export interface TimeseriesContainerStats {
  pointCount: number;
  channelCount: number;
  estimatedSizeBytes: number;
  recentPointsLast10s: number;
  ingestRateBytesPerSec: number;
}

export function useFetchTimeseriesContainerStats(containerId: number) {
  const stats = ref<TimeseriesContainerStats | null>(null);
  const loading = ref(false);

  async function fetchStats() {
    if (loading.value) return;
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) return;
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId}/stats`;
      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
        credentials: "include",
      });
      if (res.ok) {
        stats.value = await res.json();
      }
    } catch {
      // non-fatal — stats are decorative
    } finally {
      loading.value = false;
    }
  }

  fetchStats();

  return { stats, loading, refresh: fetchStats };
}
