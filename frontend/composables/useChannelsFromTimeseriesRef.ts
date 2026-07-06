/**
 * SCENEGRAPH-CANVAS-ANIM-1 slice 2 — fetch UrdfPickerChannel[] from a
 * TimeseriesReference appId via:
 *   GET /v2/references/{refAppId} → payload.timeseriesContainerAppId
 *   GET /v2/containers/{containerAppId}/channels?page=0&pageSize=500 → flat array
 *
 * Paginates up to 4 pages (2 000 channels max). Returns the channels in the
 * format consumed by BindChannelsDialog (UrdfPickerChannel[]).
 *
 * Backlog: SCENEGRAPH-CANVAS-ANIM-1.
 */
import { ref } from "vue";
import { MAX_CHANNEL_PAGE_SIZE } from "~/utils/channelConstants";
import type { UrdfPickerChannel } from "~/utils/urdfChannelPicker";

function v2BaseUrl(): string {
  const { public: publicConfig } = useRuntimeConfig();
  const cfg = publicConfig as Record<string, unknown>;
  const explicit = cfg.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (cfg.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "").replace(/\/$/, "");
}

export function useChannelsFromTimeseriesRef() {
  const channels = ref<UrdfPickerChannel[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load(referenceAppId: string): Promise<void> {
    if (!referenceAppId.trim()) {
      channels.value = [];
      return;
    }
    loading.value = true;
    error.value = null;
    channels.value = [];

    const { data: authData } = useAuth();
    const token = authData.value?.accessToken;
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    try {
      const base = v2BaseUrl();

      // Resolve reference → containerAppId (same as useJointTracksLoader)
      const refRes = await fetch(`${base}/v2/references/${referenceAppId}`, { headers });
      if (!refRes.ok) {
        error.value = `Could not fetch reference (HTTP ${refRes.status})`;
        return;
      }
      const refData = (await refRes.json()) as {
        payload?: { timeseriesContainerAppId?: string };
      };
      const containerAppId = refData.payload?.timeseriesContainerAppId;
      if (!containerAppId) {
        error.value = "This reference does not point to a timeseries container.";
        return;
      }

      // List channels — endpoint returns a flat array; paginate until exhausted
      const MAX_PAGES = 4;
      const all: UrdfPickerChannel[] = [];
      for (let page = 0; page < MAX_PAGES; page++) {
        const qs = new URLSearchParams({ page: String(page), pageSize: String(MAX_CHANNEL_PAGE_SIZE) });
        const chRes = await fetch(
          `${base}/v2/containers/${containerAppId}/channels?${qs}`,
          { headers },
        );
        if (!chRes.ok) {
          if (page === 0) error.value = `Could not list channels (HTTP ${chRes.status})`;
          break;
        }
        const rows = (await chRes.json()) as {
          shepardId: string;
          measurement?: string;
          device?: string;
          location?: string;
          symbolicName?: string;
          field?: string;
        }[];
        for (const ch of rows) {
          all.push({
            shepardId: ch.shepardId,
            measurement: ch.measurement,
            device: ch.device,
            location: ch.location,
            symbolicName: ch.symbolicName,
            field: ch.field,
          });
        }
        if (rows.length < MAX_CHANNEL_PAGE_SIZE) break;
      }
      channels.value = all;
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to load channels";
    } finally {
      loading.value = false;
    }
  }

  function clear(): void {
    channels.value = [];
    error.value = null;
  }

  return { channels, loading, error, load, clear };
}
