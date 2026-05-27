/**
 * UX-PIN1 — Fetch the v2 channel listing for a TimeseriesContainer.
 *
 * The v2 endpoint (`/v2/timeseries-containers/{id}/channels`) returns
 * `TimeseriesChannelV2IO` which includes `shepardId` alongside the legacy
 * 5-tuple.  The v1 `getTimeseriesOfContainer` endpoint does NOT include
 * `shepardId` (frozen by V1WireFidelityTest).
 *
 * This composable builds a `5-tuple → shepardId` map so that pin-button
 * handlers in the measurements table can resolve `shepardId` without a second
 * user-visible fetch.
 */
import { ref } from "vue";

interface ChannelV2 {
  shepardId: string;
  id: number;
  containerId: number;
  measurement: string;
  device: string;
  location: string;
  symbolicName: string;
  field: string;
}

function tupleKey(
  m: string | null | undefined,
  d: string | null | undefined,
  l: string | null | undefined,
  sn: string | null | undefined,
  f: string | null | undefined,
): string {
  const n = (s: string | null | undefined) => (s ?? "").trim();
  return `${n(m)}|${n(d)}|${n(l)}|${n(sn)}|${n(f)}`;
}

export function useFetchV2Channels(containerId: number) {
  // useRuntimeConfig and useAuth must be called inside the composable
  // function body (inside the Vue composition context, not at module scope).
  const { public: publicConfig } = useRuntimeConfig();
  const { data: authData } = useAuth();

  function v2Base(): string {
    const explicit = publicConfig.backendV2ApiUrl as string | undefined;
    if (explicit && explicit.length > 0) return explicit;
    return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
  }

  function authHeaders(): Record<string, string> {
    const token = authData.value?.accessToken;
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  const channelMap = ref<Map<string, ChannelV2>>(new Map());
  const loading = ref(false);

  async function load() {
    loading.value = true;
    try {
      const res = await fetch(
        `${v2Base()}/v2/timeseries-containers/${containerId}/channels?size=2000`,
        { headers: authHeaders() },
      );
      if (!res.ok) return;
      const list: ChannelV2[] = await res.json();
      const map = new Map<string, ChannelV2>();
      for (const ch of list) {
        map.set(tupleKey(ch.measurement, ch.device, ch.location, ch.symbolicName, ch.field), ch);
      }
      channelMap.value = map;
    } catch {
      // Non-critical — pin button degrades gracefully if the map is empty.
    } finally {
      loading.value = false;
    }
  }

  /**
   * Resolve a legacy 5-tuple to the channel's `shepardId`.
   * Returns `null` if the v2 map has not loaded yet or the channel is not found.
   */
  function resolveShepardId(
    measurement: string | null | undefined,
    device: string | null | undefined,
    location: string | null | undefined,
    symbolicName: string | null | undefined,
    field: string | null | undefined,
  ): string | null {
    return channelMap.value.get(tupleKey(measurement, device, location, symbolicName, field))?.shepardId ?? null;
  }

  load();

  return { channelMap, loading, resolveShepardId };
}
