/**
 * CC1b — fetch DataObjects linked to a FileContainer via their references.
 *
 * Calls GET /v2/file-containers/{containerId}/linked-data-objects.
 * Uses raw fetch (same pattern as usePublishEntity / FileContainerAccessor)
 * because the v2 endpoints are not in the generated backend-client base path.
 *
 * Takes the numeric container id from the route (same id used by the legacy
 * /shepard/api/fileContainers/{id} surface) so no wait for appId is needed.
 */

import type { DataObject } from "@dlr-shepard/backend-client";

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFileContainerLinkedDataObjects(containerId: number) {
  const dataObjects = ref<DataObject[]>([]);
  const isLoading = ref(true);

  async function fetchLinkedDataObjects() {
    isLoading.value = true;
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    try {
      const url = `${v2BaseUrl()}/v2/file-containers/${containerId}/linked-data-objects`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.ok) {
        dataObjects.value = (await response.json()) as DataObject[];
      } else {
        handleError(
          new Error(`HTTP ${response.status}`),
          "fetchFileContainerLinkedDataObjects",
        );
        dataObjects.value = [];
      }
    } catch (e) {
      handleError(e as Error, "fetchFileContainerLinkedDataObjects");
      dataObjects.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  fetchLinkedDataObjects();

  return { dataObjects, isLoading };
}
