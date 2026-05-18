/**
 * TS_CHART_VIEW1 — fetch and PATCH the per-container chart-view config.
 *
 * Wire endpoints:
 *   GET   /v2/timeseries-containers/{containerId}/chart-view
 *   PATCH /v2/timeseries-containers/{containerId}/chart-view
 *
 * Returns:
 *   selectedChannelKeys (ref) — current persisted curated selection;
 *     empty array when nothing is configured.
 *   loading (ref) — true during fetch.
 *   saving (ref) — true during PATCH.
 *   save(keys: string[]) — replaces the curated selection.
 *   refresh() — re-fetches the server state.
 */

interface ChartViewDto {
  selectedChannels?: string[];
  updatedAt?: number;
  updatedBy?: string;
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

export function useTimeseriesContainerChartView(containerId: number) {
  const selectedChannelKeys = ref<string[]>([]);
  const updatedAt = ref<number | undefined>(undefined);
  const updatedBy = ref<string | undefined>(undefined);
  const loading = ref(true);
  const saving = ref(false);

  async function refresh() {
    loading.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId}/chart-view`;
      const response = await fetch(url, { headers });
      if (response.ok) {
        const data = (await response.json()) as ChartViewDto;
        selectedChannelKeys.value = data.selectedChannels ?? [];
        updatedAt.value = data.updatedAt;
        updatedBy.value = data.updatedBy;
      } else {
        selectedChannelKeys.value = [];
      }
    } catch (e) {
      // Permission errors or backend-not-ready — treat as "no curated view".
      // The chart falls back to first-MAX_CHANNELS in that case.
      selectedChannelKeys.value = [];
    } finally {
      loading.value = false;
    }
  }

  async function save(keys: string[]): Promise<boolean> {
    saving.value = true;
    try {
      const headers = await authHeaders();
      const url = `${v2BaseUrl()}/v2/timeseries-containers/${containerId}/chart-view`;
      const response = await fetch(url, {
        method: "PATCH",
        headers,
        body: JSON.stringify({ selectedChannels: keys }),
      });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const data = (await response.json()) as ChartViewDto;
      selectedChannelKeys.value = data.selectedChannels ?? [];
      updatedAt.value = data.updatedAt;
      updatedBy.value = data.updatedBy;
      emitSuccess("Channel overview saved");
      return true;
    } catch (e) {
      handleError(e as Error, "saving chart view");
      return false;
    } finally {
      saving.value = false;
    }
  }

  refresh();

  return {
    selectedChannelKeys,
    updatedAt,
    updatedBy,
    loading,
    saving,
    refresh,
    save,
  };
}
