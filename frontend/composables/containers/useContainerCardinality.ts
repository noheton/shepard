/**
 * UI21-SIZEBAR-DATA — per-row cardinality fetch for the container list sizebar.
 *
 * Calls the appropriate `/v2/{kind}-containers/{id}/summary` endpoint and
 * returns the per-kind item count:
 *   - TIMESERIES  → channel count (SQL COUNT over timeseries rows)
 *   - FILE        → file count (Neo4j relationship list)
 *   - STRUCTUREDDATA → record count (Neo4j relationship list)
 *
 * Unsupported types (BASIC, SPATIALDATA, etc.) return null immediately.
 * This is intentionally lazy/per-row: callers mount only when the row is
 * visible, so the requests on one page are fire-and-forget and do not
 * block the table render.
 */

type SupportedKind = "FILE" | "TIMESERIES" | "STRUCTUREDDATA";

const SUPPORTED_KINDS: Set<string> = new Set<SupportedKind>([
  "FILE",
  "TIMESERIES",
  "STRUCTUREDDATA",
]);

interface ContainerCardinalityIO {
  cardinality: number | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function summaryUrl(containerId: number, type: string): string | null {
  switch (type) {
    case "FILE":
      return `${v2BaseUrl()}/v2/file-containers/${containerId}/summary`;
    case "TIMESERIES":
      return `${v2BaseUrl()}/v2/timeseries-containers/${containerId}/summary`;
    case "STRUCTUREDDATA":
      return `${v2BaseUrl()}/v2/structured-data-containers/${containerId}/summary`;
    default:
      return null;
  }
}

/**
 * Returns `{ cardinality, isLoading }` where:
 *   - `cardinality` is `null` when the type is unsupported or the fetch failed,
 *     or a non-negative integer when the count is known.
 *   - `isLoading` flips to false once the fetch settles.
 */
export function useContainerCardinality(
  containerId: number,
  containerType: string,
) {
  const cardinality = ref<number | null>(null);
  const isLoading = ref(SUPPORTED_KINDS.has(containerType));

  if (!SUPPORTED_KINDS.has(containerType)) {
    isLoading.value = false;
    return { cardinality, isLoading };
  }

  const url = summaryUrl(containerId, containerType);
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
        const body = (await response.json()) as ContainerCardinalityIO;
        cardinality.value = body.cardinality ?? null;
      }
      // Non-2xx: leave cardinality as null (neutral bar, no tooltip count).
    } catch {
      // Network or parse error — leave cardinality as null.
    } finally {
      isLoading.value = false;
    }
  })();

  return { cardinality, isLoading };
}
