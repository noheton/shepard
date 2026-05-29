import {
  TimeseriesContainerApi,
  type TimeseriesEntity,
} from "@dlr-shepard/backend-client";
import { useIntersectionObserver } from "@vueuse/core";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * TS-IDc — v2 channel-data endpoint URL.
 *
 * When the caller knows the channel's {@code shepardId} (single-field UUID,
 * minted by the V1.11.0 substrate migration), we prefer the
 * {@code /v2/timeseries-containers/{cid}/channels/{shepardId}/data} path
 * over the legacy 5-tuple {@code /timeseriesContainers/.../timeseries}
 * lookup. This trades five string-equality SQL predicates for one
 * index-only B-tree probe — the planning vs. execution ratio drops from
 * the 17× tuple-walk to roughly 1:1 on the warm-cache path, per
 * {@code TS-AUDIT-2026-05-24-009}.
 *
 * The legacy path stays as a fallback for callers that don't yet carry a
 * shepardId on the channel object (transition window — TS-IDd will close
 * this by component-by-component migration).
 */
function v2Base(publicConfig: Record<string, unknown>): string {
  const explicit = publicConfig.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit;
  return (publicConfig.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

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
 *
 * PERF7: in-flight de-duplication. A module-level Map keyed by the full
 * channel identity (containerId + 5-tuple + downsample + maxPoints) ensures
 * that if multiple component instances request the same preview concurrently
 * (e.g. "expand all" on a 200-channel container), only one HTTP request fires;
 * every caller shares the same Promise. The entry is removed once the Promise
 * settles so a subsequent fetch always gets fresh data.
 */

/**
 * Build a stable cache key for a given preview request.
 *
 * When {@code channelShepardId} is present the key uses that single
 * field — two distinct 5-tuples that resolve to the same shepardId
 * (after a channel rename, say) collapse to one cache entry. When
 * shepardId is absent the key falls back to the legacy 5-tuple.
 */
function previewKey(
  containerId: number,
  channel: TimeseriesEntity,
  downsample: boolean,
  maxPoints: number,
  channelShepardId?: string | null,
): string {
  if (channelShepardId) {
    return [containerId, "sid", channelShepardId, String(downsample), String(maxPoints)].join("|");
  }
  return [
    containerId,
    channel.measurement ?? "",
    channel.device ?? "",
    channel.location ?? "",
    channel.symbolicName ?? "",
    channel.field ?? "",
    String(downsample),
    String(maxPoints),
  ].join("|");
}

/** Module-level in-flight request registry (cleared after each Promise settles). */
const inFlight = new Map<string, Promise<Array<[number, number]>>>();

/**
 * Perform (or join) an in-flight fetch for a channel preview.
 *
 * If an identical request is already in-flight the same Promise is returned;
 * this collapses concurrent identical requests to a single HTTP call.
 */
async function fetchPreviewData(
  containerId: number,
  channel: TimeseriesEntity,
  downsample: boolean,
  maxPoints: number,
  channelShepardId?: string | null,
): Promise<Array<[number, number]>> {
  const key = previewKey(containerId, channel, downsample, maxPoints, channelShepardId);

  const existing = inFlight.get(key);
  if (existing) return existing;

  const promise = (async (): Promise<Array<[number, number]>> => {
    const endNs = Date.now() * 1e6; // ms → ns

    // TS-IDc preferred path: single-field shepardId. The v2 path-param
    // endpoint isn't in the generated client yet, so we hit it directly
    // with the runtime-config-derived base URL + raw fetch — same shape
    // PinnedChannelTile already uses successfully against prod.
    if (channelShepardId) {
      const { public: publicConfig } = useRuntimeConfig();
      const { data: authData } = useAuth();
      const token = authData.value?.accessToken;
      const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

      const qs = new URLSearchParams();
      qs.set("start", "0");
      qs.set("end", String(endNs));
      if (downsample) {
        qs.set("downsample", "lttb");
        qs.set("max_points", String(maxPoints));
      }
      const url =
        `${v2Base(publicConfig as Record<string, unknown>)}/v2/timeseries-containers/${containerId}` +
        `/channels/${channelShepardId}/data?${qs.toString()}`;

      const res = await fetch(url, { headers });
      if (!res.ok) throw new Error(`HTTP ${res.status} on TS-IDc preview fetch`);
      const body: { points?: Array<{ timestamp: number; value: number }> } = await res.json();
      return (body.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
    }

    // Legacy 5-tuple path (transition bridge until TS-IDd closes the loop).
    const result = await useShepardApi(TimeseriesContainerApi).value.getTimeseries({
      timeseriesContainerId: containerId,
      measurement: channel.measurement ?? "",
      device: channel.device ?? "",
      location: channel.location ?? "",
      symbolicName: channel.symbolicName ?? "",
      field: channel.field ?? "",
      start: 0,
      end: endNs,
      ...(downsample ? { downsample: "lttb", maxPoints } : {}),
    });
    return (result.points ?? []).map(p => [p.timestamp, p.value] as [number, number]);
  })();

  inFlight.set(key, promise);
  promise.finally(() => inFlight.delete(key));

  return promise;
}

export interface UseFetchChannelPreviewOptions {
  downsample?: boolean;
  maxPoints?: number;
  /**
   * TS-IDc — single-field channel identity. When supplied, the composable
   * hits {@code /v2/timeseries-containers/{cid}/channels/{shepardId}/data}
   * instead of the legacy 5-tuple endpoint. Source: the v2 channels
   * listing populated by {@link useFetchV2Channels}.
   */
  channelShepardId?: string | null;
}

/**
 * Base channel preview fetch composable (eager — fires immediately on creation).
 *
 * Exported separately so callers that manage visibility themselves (e.g. tests)
 * can use this directly without `IntersectionObserver` involvement.
 */
export function useFetchChannelPreview(
  containerId: number,
  channel: TimeseriesEntity,
  options?: UseFetchChannelPreviewOptions,
) {
  const downsampleOpt = options?.downsample ?? true;
  const maxPoints = options?.maxPoints ?? 2000;
  const channelShepardId = options?.channelShepardId ?? null;

  const data = ref<Array<[number, number]>>([]);
  const loading = ref(false);
  const downsampled = ref(false);

  async function fetch(downsample: boolean = downsampleOpt) {
    loading.value = true;
    try {
      data.value = await fetchPreviewData(
        containerId, channel, downsample, maxPoints, channelShepardId);
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

/**
 * PERF7 — Lazy channel preview composable.
 *
 * Identical API to {@link useFetchChannelPreview} but delays the initial fetch
 * until the target element scrolls into the viewport.  Uses
 * `useIntersectionObserver` from `@vueuse/core`; the observer is stopped after
 * the first visible intersection so repeated scroll-in/out does not re-fetch.
 *
 * {@code refetch} bypasses the observer entirely — user-initiated actions (e.g.
 * toggling "Full" mode) always fire immediately.
 *
 * Usage in a component:
 *
 * ```ts
 * const rootEl = ref<HTMLElement | null>(null);
 * const { data, loading, downsampled, refetch } =
 *   useChannelPreviewLazy(containerId, channel, rootEl);
 * ```
 *
 * ```html
 * <div ref="rootEl">…</div>
 * ```
 */
export function useChannelPreviewLazy(
  containerId: number,
  channel: TimeseriesEntity,
  target: Ref<HTMLElement | null>,
  options?: UseFetchChannelPreviewOptions,
) {
  const downsampleOpt = options?.downsample ?? true;
  const maxPoints = options?.maxPoints ?? 2000;
  const channelShepardId = options?.channelShepardId ?? null;

  const data = ref<Array<[number, number]>>([]);
  const loading = ref(false);
  const downsampled = ref(false);
  const fetched = ref(false);

  /** Direct fetch — used by refetch() and the intersection callback. */
  async function doFetch(downsample: boolean = downsampleOpt) {
    loading.value = true;
    try {
      data.value = await fetchPreviewData(
        containerId, channel, downsample, maxPoints, channelShepardId);
      downsampled.value = downsample;
    } catch {
      data.value = [];
      downsampled.value = false;
    } finally {
      loading.value = false;
    }
  }

  // `useIntersectionObserver` requires a Vue component context; calling it
  // inside a composable that is itself called from setup() is safe.
  let stopObserver: (() => void) | undefined;

  const { stop } = useIntersectionObserver(
    target,
    ([entry]: IntersectionObserverEntry[]) => {
      if (entry?.isIntersecting && !fetched.value) {
        fetched.value = true;
        stop(); // one-shot — don't re-fetch on every scroll-in
        doFetch();
      }
    },
    { threshold: 0.1 },
  );
  stopObserver = stop;

  /**
   * User-initiated re-fetch (e.g. toggling the "Full" / downsampled switch).
   * Always fires immediately, regardless of visibility state.
   */
  async function refetch(downsample: boolean = downsampleOpt) {
    fetched.value = true; // prevent the observer from also firing
    await doFetch(downsample);
  }

  return { data, loading, downsampled, refetch, stopObserver };
}

/** Exported for testing — allows tests to inspect or clear the in-flight map. */
export { inFlight as _inFlightMap };
