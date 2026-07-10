/**
 * TS-CROSS-DO-VIEW-2-FE — composable for the cross-DataObject bulk-data
 * endpoint shipped in TS-CROSS-DO-VIEW-1.
 *
 * Endpoint: POST /v2/data-objects/cross-bulk?kind=timeseries
 *
 * Used by the "Cross-track view" pane on Collection detail
 * (`CollectionCrossTrackViewPane.vue`). One predicate, many DataObjects,
 * one time window, returns one downsampled series per DO. DataObjects
 * without a matching channel return an empty `points` list; DOs the
 * caller can't read are silently dropped.
 */

export interface CrossDoPoint {
  timestamp: number; // absolute UTC nanoseconds
  value: unknown;
}

export interface CrossDoSeries {
  dataObjectAppId: string;
  dataObjectName: string | null;
  channelKey: string;
  channelSymbolicName: string | null;
  points: CrossDoPoint[];
}

export interface CrossDoRequest {
  dataObjectAppIds: string[];
  channelPredicate: string;
  start: number; // ns since epoch
  end: number;
  downsampleTo?: number;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeaders(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
    "Content-Type": "application/json",
  };
}

export function useCrossDoBulkData() {
  const series = ref<CrossDoSeries[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchCrossDo(body: CrossDoRequest): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/data-objects/cross-bulk?kind=timeseries`;
      const response = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        error.value = `HTTP ${response.status}`;
        series.value = [];
        return;
      }
      series.value = (await response.json()) as CrossDoSeries[];
    } catch (e) {
      error.value = (e as Error).message;
      series.value = [];
    } finally {
      loading.value = false;
    }
  }

  return { series, loading, error, fetchCrossDo };
}
