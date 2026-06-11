/**
 * CC1e — for a container row on the /containers list page, resolve the
 * distinct collection IDs that reference the container via their DataObjects.
 *
 * Strategy: call the appropriate CC1b `linked-data-objects` endpoint for
 * FILE / TIMESERIES / STRUCTUREDDATA containers and derive unique collectionIds
 * from the returned DataObject list.  Container types without a CC1b endpoint
 * (BASIC, SPATIALDATA) return `null` to signal "unsupported".
 *
 * This is intentionally lazy/per-row: callers mount only when the row is
 * visible (v-for in a real table body), so the 20 concurrent requests on one
 * page are fire-and-forget and do not block the table render.
 */

import type { DataObject } from "@dlr-shepard/backend-client";

type SupportedType = "FILE" | "TIMESERIES" | "STRUCTUREDDATA";

const SUPPORTED_TYPES: Set<string> = new Set<SupportedType>([
  "FILE",
  "TIMESERIES",
  "STRUCTUREDDATA",
]);

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function linkedDataObjectsUrl(containerId: string | number, type: string): string | null {
  switch (type) {
    case "FILE":
      return `${v2BaseUrl()}/v2/file-containers/${containerId}/linked-data-objects`;
    case "TIMESERIES":
      // v2 endpoint is keyed by containerAppId (UUID v7 string); numeric ids
      // will 404 — callers must pass the appId for timeseries containers.
      return `${v2BaseUrl()}/v2/timeseries-containers/${containerId}/linked-data-objects`;
    case "STRUCTUREDDATA":
      return `${v2BaseUrl()}/v2/structured-data-containers/${containerId}/linked-data-objects`;
    default:
      return null;
  }
}

export function useContainerReferencedByCollections(
  containerId: string | number,
  containerType: string,
) {
  /**
   * null  = container type has no CC1b endpoint ("—" placeholder)
   * []    = no linked data objects found (0 collections)
   * [...]  = distinct collection IDs
   */
  const collectionIds = ref<number[] | null>(
    SUPPORTED_TYPES.has(containerType) ? [] : null,
  );
  const isLoading = ref(SUPPORTED_TYPES.has(containerType));

  if (!SUPPORTED_TYPES.has(containerType)) {
    isLoading.value = false;
    return { collectionIds, isLoading };
  }

  const url = linkedDataObjectsUrl(containerId, containerType);
  if (!url) {
    // Defensive — should not happen given the guard above.
    collectionIds.value = null;
    isLoading.value = false;
    return { collectionIds, isLoading };
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
        const dataObjects = (await response.json()) as DataObject[];
        // Derive unique collection IDs from the DataObject list.
        const ids = [...new Set(dataObjects.map((d) => d.collectionId))];
        collectionIds.value = ids;
      } else {
        // Non-2xx (e.g. 403 on a container the user cannot read) — treat as
        // 0 collections rather than an error, to keep the table row clean.
        collectionIds.value = [];
      }
    } catch {
      // Network or parse error — show 0 rather than crashing the row.
      collectionIds.value = [];
    } finally {
      isLoading.value = false;
    }
  })();

  return { collectionIds, isLoading };
}
