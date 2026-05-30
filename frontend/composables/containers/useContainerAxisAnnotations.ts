/**
 * UX-WALK-2026-05-29-02 — fetch spatial:axis annotations from all channels
 * of a TimeseriesContainer and expose them as a flat list of chips.
 *
 * The `spatial:axis` annotations are stored on `:AnnotatableTimeseries` bridge
 * nodes (per-channel), not on the container entity itself. The container-level
 * Semantic Annotations panel calls `GET /v2/timeseries-containers/{id}/annotations`
 * which only returns container-entity annotations — it never sees channel-level
 * axis annotations. This composable bridges that gap by:
 *
 *   1. Fetching the channel list from GET /v2/timeseries-containers/{id}/channels
 *   2. For each channel that has a shepardId, fetching
 *      GET /v2/timeseries-containers/{id}/channels/{shepardId}/annotations
 *   3. Filtering the results to propertyIRI = "urn:shepard:spatial:axis"
 *   4. Returning a flat list of { channelName, shepardId, value } records
 *      suitable for rendering as annotation chips.
 *
 * Fan-out is bounded: containers with thousands of channels fire one request
 * per channel, but axis annotations are only expected on a small subset
 * (typically 3–6 channels out of many). We eagerly resolve all channels once
 * and then only request annotations for channels whose shepardId is known.
 */

import type { SemanticAnnotation } from "@dlr-shepard/backend-client";

export interface AxisAnnotationChip {
  shepardId: string;
  channelName: string;
  annotation: SemanticAnnotation;
}

/** propertyIRI written by AnnotatableTimeseriesService.createAnnotationForChannel */
export const TS_AXIS_PREDICATE = "urn:shepard:spatial:axis";

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
  };
}

interface ChannelV2 {
  shepardId?: string | null;
  measurement?: string | null;
  device?: string | null;
  location?: string | null;
  symbolicName?: string | null;
  field?: string | null;
}

function channelLabel(ch: ChannelV2): string {
  const parts = [ch.device, ch.field, ch.location, ch.measurement, ch.symbolicName].filter(Boolean);
  return parts.length ? parts.join(" · ") : "(unnamed channel)";
}

export function useContainerAxisAnnotations(containerId: number) {
  const chips = ref<AxisAnnotationChip[]>([]);
  const loading = ref(false);

  async function fetchAxisAnnotations() {
    loading.value = true;
    chips.value = [];
    try {
      const base = v2BaseUrl();
      const headers = await authHeaders();

      const channelRes = await fetch(
        `${base}/v2/timeseries-containers/${containerId}/channels?size=2000`,
        { headers },
      );
      if (!channelRes.ok) return;
      const channels: ChannelV2[] = await channelRes.json();

      const channelsWithId = channels.filter(ch => ch.shepardId);
      const results: AxisAnnotationChip[] = [];

      await Promise.all(
        channelsWithId.map(async ch => {
          try {
            const annRes = await fetch(
              `${base}/v2/timeseries-containers/${containerId}/channels/${ch.shepardId}/annotations`,
              { headers },
            );
            if (!annRes.ok) return;
            const annotations: SemanticAnnotation[] = await annRes.json();
            for (const ann of annotations) {
              if (ann.propertyIRI === TS_AXIS_PREDICATE) {
                results.push({
                  shepardId: ch.shepardId!,
                  channelName: channelLabel(ch),
                  annotation: ann,
                });
              }
            }
          } catch {
            // Non-critical — degrade silently for this channel.
          }
        }),
      );

      chips.value = results;
    } catch (e) {
      handleError(e as Error, "fetching axis annotations");
    } finally {
      loading.value = false;
    }
  }

  fetchAxisAnnotations();

  return { chips, loading, fetchAxisAnnotations };
}
