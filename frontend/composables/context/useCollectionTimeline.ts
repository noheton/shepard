/**
 * COLL-TIMELINE-1 — composable for
 * `GET /v2/collections/{collectionAppId}/timeline`.
 *
 * One canonical timeline envelope per Collection. The component
 * (`CollectionTimelinePane.vue`) lazy-loads on expansion to keep the
 * Collection-detail page's initial paint cheap.
 *
 * Adaptive bin size: caller passes `binSizeDays`; the server may coarsen
 * upward to fit the campaign span — the response echoes the actually-used
 * size in `binSizeDays`. Components must render off the echoed value, not
 * the requested one.
 *
 * Cache headers: the backend stamps `Cache-Control: max-age=300, must-revalidate`,
 * so a re-fetch within five minutes hits the browser HTTP cache. We rely on
 * that rather than an in-memory dedup map.
 */

export interface CollectionTimelineBin {
  day: string; // ISO date (e.g. "2024-04-15") of the bin's first day, UTC
  count: number;
  ncrCount: number;
  rejectCount: number;
}

export interface CollectionTimelineLane {
  key: string; // stable URL-safe slug (e.g. "afp-layup")
  label: string; // human-readable label (e.g. "AFP Layup")
  bins: CollectionTimelineBin[];
}

export interface CollectionTimelineEnvelope {
  binSizeDays: number;
  rangeStart: string | null;
  rangeEnd: string | null;
  totalDataObjects: number;
  lanes: CollectionTimelineLane[];
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = (config as { backendV2ApiUrl?: string }).backendV2ApiUrl;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function authHeader(): Promise<Record<string, string>> {
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  if (!accessToken) throw new Error("Not authenticated");
  return {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
  };
}

export function useCollectionTimeline() {
  const envelope = ref<CollectionTimelineEnvelope | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function fetchTimeline(collectionAppId: string, binSizeDays = 1, bypassCache = false): Promise<void> {
    if (!collectionAppId) return;
    loading.value = true;
    error.value = null;
    try {
      const headers = await authHeader();
      const url =
        `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/timeline` +
        `?binSizeDays=${encodeURIComponent(String(binSizeDays))}`;
      const fetchInit: RequestInit = { headers };
      if (bypassCache) fetchInit.cache = "no-cache";
      const response = await fetch(url, fetchInit);
      if (!response.ok) {
        error.value = `HTTP ${response.status}`;
        envelope.value = null;
        return;
      }
      envelope.value = (await response.json()) as CollectionTimelineEnvelope;
    } catch (e) {
      error.value = (e as Error).message;
      envelope.value = null;
    } finally {
      loading.value = false;
    }
  }

  return { envelope, loading, error, fetchTimeline };
}
