/**
 * UI21-SIZEBAR-DATA — lazy fire-and-forget cardinality summary for a container.
 *
 * Calls the per-kind summary endpoint:
 *   GET /v2/timeseries-containers/{id}/summary
 *   GET /v2/file-containers/{id}/summary
 *   GET /v2/structured-data-containers/{id}/summary
 *
 * Returns { cardinality, lastUpdated } or null if the fetch fails / is pending.
 * Errors are swallowed — the sizebar is decorative, never blocking.
 */

import type { ContainerType } from "@dlr-shepard/backend-client";

export interface ContainerCardinalitySummary {
  cardinality: number;
  lastUpdated: string;
}

/** Map container type → URL path segment used in /v2/ summary endpoints. */
const summaryPathSegment: Partial<Record<ContainerType, string>> = {
  TIMESERIES: "timeseries-containers",
  FILE: "file-containers",
  STRUCTUREDDATA: "structured-data-containers",
};

function getV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  return explicit && explicit.length > 0
    ? explicit
    : (config.backendApiUrl as string).replace(/\/shepard\/api\/?$/, "");
}

/**
 * Fetch the cardinality summary for a single container.
 * Returns null for container kinds without a summary endpoint (e.g. SPATIALDATA, BASIC).
 *
 * @param containerId  Numeric OGM id of the container.
 * @param containerType  The container's type discriminator.
 */
export function useContainerCardinalitySummary(
  containerId: number,
  containerType: ContainerType,
) {
  const summary = ref<ContainerCardinalitySummary | null>(null);
  const loading = ref(false);

  const pathSegment = summaryPathSegment[containerType];
  if (!pathSegment) {
    // No summary endpoint for this container kind — leave cardinality null.
    return { summary, loading };
  }

  async function fetchSummary() {
    if (loading.value) return;
    loading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      if (!accessToken) return;
      const url = `${getV2BaseUrl()}/v2/${pathSegment}/${containerId}/summary`;
      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
        credentials: "include",
      });
      if (res.ok) {
        summary.value = await res.json();
      }
    } catch {
      // Non-fatal — sizebar is decorative.
    } finally {
      loading.value = false;
    }
  }

  fetchSummary();

  return { summary, loading };
}
