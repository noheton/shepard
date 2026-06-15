/**
 * TS_STATS1 — fetches storage + ingest stats for a TimeseriesContainer.
 * Calls GET /v2/containers/{containerAppId}/stats.
 * (APISIMP-CONT-NS-COLLAPSE-7 — migrated from /v2/timeseries-containers/)
 *
 * Accepts a static string or a Ref<string | null> so callers that resolve
 * appId asynchronously (via a container accessor) can pass a computed ref and
 * the fetch fires once the appId becomes non-null.
 */
import type { Ref } from "vue";

export interface TimeseriesContainerStats {
  pointCount: number;
  channelCount: number;
  estimatedSizeBytes: number;
  recentPointsLast10s: number;
  ingestRateBytesPerSec: number;
}

function getV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

export function useFetchTimeseriesContainerStats(containerAppId: string | Ref<string | null>) {
  const appIdRef: Ref<string | null> = isRef(containerAppId)
    ? containerAppId
    : ref(containerAppId || null);
  const stats = ref<TimeseriesContainerStats | null>(null);
  const loading = ref(false);

  async function fetchStats() {
    const id = appIdRef.value;
    if (!id || loading.value) return;
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) return;
      const url = `${getV2BaseUrl()}/v2/containers/${encodeURIComponent(id)}/stats`;
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

  watch(appIdRef, (id) => { if (id) fetchStats(); }, { immediate: true });

  return { stats, loading, refresh: fetchStats };
}
