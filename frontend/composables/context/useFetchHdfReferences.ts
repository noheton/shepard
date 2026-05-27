/**
 * REF-UNIFIED-TABLE — fetch HdfReferences for a DataObject.
 *
 * HdfReference is served at `/v2/data-objects/{dataObjectAppId}/hdf-references`.
 * Not yet in `@dlr-shepard/backend-client`, so raw fetch() is used with a
 * manually-typed response shape — same pattern as useFetchVideoStreamReferences.ts.
 *
 * Handles 404 gracefully (returns empty list) for when shepard.hdf.enabled=false.
 */

export interface HdfReferenceIO {
  appId: string;
  hdfContainerAppId?: string;
  datasetPath?: string;
  description?: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchHdfReferences(dataObjectAppId: string) {
  const references = ref<HdfReferenceIO[]>([]);
  const isLoading = ref(false);
  const fetchError = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    fetchError.value = null;

    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      fetchError.value = "Not authenticated";
      isLoading.value = false;
      return;
    }

    const url = `${v2BaseUrl()}/v2/data-objects/${encodeURIComponent(dataObjectAppId)}/hdf-references`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        // HDF feature off or DataObject not found; treat as empty list.
        references.value = [];
        return;
      }
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch HDF references (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listHdfReferences");
        return;
      }
      references.value = (await response.json()) as HdfReferenceIO[];
    } catch (error) {
      const message = error instanceof Error ? error.message : "Network error";
      fetchError.value = message;
      handleError(message, "listHdfReferences");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { references, isLoading, fetchError, refresh };
}
