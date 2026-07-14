/**
 * Load JointTrack[] from a TimeseriesReference appId + joint-channel bindings.
 *
 * Used by the scene-graph play page (SCENEGRAPH-CANVAS-ANIM-1) to drive
 * UrdfAnimator with live channel data fetched on demand.
 *
 * Flow:
 *  1. GET /v2/references/{referenceAppId} → timeseriesContainerAppId + ns time window
 *  2. For each binding, GET /v2/containers/{containerAppId}/channels/{channelId}/data
 *  3. Convert ns timestamps → ms; return JointTrack[].
 *
 * The caller passes sampleTimeUnitsPerMs=1 (default) to UrdfAnimator since
 * samples are already in milliseconds after the ns→ms conversion here.
 */
import { ref } from "vue";
import type { JointTrack } from "~/utils/urdfAnimation";
import { nsToIso } from "~/composables/containers/useCrossDoBulkData";

export interface JointBinding {
  /** Joint name as declared in the URDF. */
  joint: string;
  /** Channel appId (shepardId) that drives this joint. */
  channelSelector: string;
}

function v2BaseUrl(): string {
  const { public: publicConfig } = useRuntimeConfig();
  const cfg = publicConfig as Record<string, unknown>;
  const explicit = cfg.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit;
  return (cfg.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

export function useJointTracksLoader() {
  const tracks = ref<JointTrack[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load(referenceAppId: string, bindings: JointBinding[]): Promise<void> {
    if (!referenceAppId || bindings.length === 0) {
      tracks.value = [];
      return;
    }

    loading.value = true;
    error.value = null;
    tracks.value = [];

    const { data: authData } = useAuth();
    const token = authData.value?.accessToken;
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    try {
      const base = v2BaseUrl();

      // Step 1: resolve the reference to get container appId + time window (ns)
      const refRes = await fetch(`${base}/v2/references/${referenceAppId}`, { headers });
      if (!refRes.ok) {
        error.value = `Could not fetch timeseries reference (HTTP ${refRes.status})`;
        return;
      }
      const refData = (await refRes.json()) as {
        payload?: { timeseriesContainerAppId?: string; start?: number; end?: number };
      };
      const containerAppId = refData.payload?.timeseriesContainerAppId;
      if (!containerAppId) {
        error.value = "Timeseries reference has no container appId — channel data unavailable";
        return;
      }
      const startNs = refData.payload?.start ?? 0;
      const endNs = refData.payload?.end ?? 0;

      // Step 2: fetch data for each bound channel in parallel
      const results = await Promise.allSettled(
        bindings
          .filter(b => b.channelSelector)
          .map(async (b): Promise<JointTrack | null> => {
            const qs = new URLSearchParams({
              start: nsToIso(startNs),
              end: nsToIso(endNs),
            });
            const dataRes = await fetch(
              `${base}/v2/containers/${containerAppId}/channels/${b.channelSelector}/data?${qs}`,
              { headers },
            );
            if (!dataRes.ok) return null;
            const body = (await dataRes.json()) as {
              points?: { timestamp: number; value: number }[];
            };
            const samples = (body.points ?? []).map(p => ({
              t: p.timestamp / 1e6, // ns → ms
              value: p.value,
            }));
            return samples.length > 0 ? { jointName: b.joint, samples } : null;
          }),
      );

      tracks.value = results
        .filter(
          (r): r is PromiseFulfilledResult<JointTrack | null> => r.status === "fulfilled",
        )
        .map(r => r.value)
        .filter((t): t is JointTrack => t !== null);
    } catch (e) {
      error.value = e instanceof Error ? e.message : "Failed to load joint tracks";
    } finally {
      loading.value = false;
    }
  }

  function clear(): void {
    tracks.value = [];
    error.value = null;
  }

  return { tracks, loading, error, load, clear };
}
