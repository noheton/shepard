/**
 * UI21-SIZEBAR-DATA — per-kind cardinality fetch for the /containers list sizebar.
 *
 * Replaces the CC1e referenced-by proxy with domain-meaningful counts:
 *   - TIMESERIES  → channelCount from GET /v2/timeseries-containers/{id}/stats
 *   - FILE        → fileCount   from GET /v2/file-containers/{id}/stats
 *   - STRUCTUREDDATA → entryCount from GET /v2/structured-data-containers/{id}/stats
 *   - Other types → null (no cardinality endpoint available)
 *
 * The fetch is lazy and fire-and-forget: callers mount per-row so the 20 concurrent
 * requests on one page do not block the table render. Errors return null so the
 * sizebar degrades gracefully to an empty bar.
 */

type SupportedKind = "TIMESERIES" | "FILE" | "STRUCTUREDDATA";

const SUPPORTED_KINDS: Set<string> = new Set<SupportedKind>([
  "TIMESERIES",
  "FILE",
  "STRUCTUREDDATA",
]);

function getV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit.replace(/\/$/, "")
    : (config.backendApiUrl as string)
        .replace(/\/shepard\/api\/?$/, "")
        .replace(/\/$/, "");
}

function statsUrlFor(containerAppId: string, type: string): string | null {
  switch (type) {
    case "TIMESERIES":
      return `${getV2BaseUrl()}/v2/timeseries-containers/${containerAppId}/stats`;
    case "FILE":
      return `${getV2BaseUrl()}/v2/file-containers/${containerAppId}/stats`;
    case "STRUCTUREDDATA":
      return `${getV2BaseUrl()}/v2/structured-data-containers/${containerAppId}/stats`;
    default:
      return null;
  }
}

function extractCardinality(type: string, body: Record<string, unknown>): number {
  switch (type) {
    case "TIMESERIES":
      return typeof body.channelCount === "number" ? body.channelCount : 0;
    case "FILE":
      return typeof body.fileCount === "number" ? body.fileCount : 0;
    case "STRUCTUREDDATA":
      return typeof body.entryCount === "number" ? body.entryCount : 0;
    default:
      return 0;
  }
}

/** Label shown alongside the sizebar value (e.g. "12 channels"). */
export function cardinalityLabel(type: string, count: number): string {
  switch (type) {
    case "TIMESERIES":
      return `${count} channel${count === 1 ? "" : "s"}`;
    case "FILE":
      return `${count} file${count === 1 ? "" : "s"}`;
    case "STRUCTUREDDATA":
      return `${count} entr${count === 1 ? "y" : "ies"}`;
    default:
      return String(count);
  }
}

/**
 * Returns the domain cardinality for a container row.
 *
 * - `cardinality` is `null` while loading, for unsupported types, or when appId is absent.
 * - `isLoading` is true while the fetch is in flight.
 */
export function useContainerCardinality(
  containerAppId: string | null | undefined,
  containerType: string,
) {
  const cardinality = ref<number | null>(null);
  const isLoading = ref(
    SUPPORTED_KINDS.has(containerType) && containerAppId != null,
  );

  if (!SUPPORTED_KINDS.has(containerType) || containerAppId == null) {
    return { cardinality, isLoading };
  }

  const url = statsUrlFor(containerAppId, containerType);
  if (!url) {
    isLoading.value = false;
    return { cardinality, isLoading };
  }

  (async () => {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.ok) {
        const body = (await response.json()) as Record<string, unknown>;
        cardinality.value = extractCardinality(containerType, body);
      }
      // Non-2xx (e.g. 403) → leave cardinality as null; sizebar shows empty.
    } catch {
      // Network or parse error — leave cardinality as null.
    } finally {
      isLoading.value = false;
    }
  })();

  return { cardinality, isLoading };
}
