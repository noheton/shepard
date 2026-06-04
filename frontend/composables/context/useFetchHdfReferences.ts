/**
 * A5 (aidocs/16) — fetch HdfReferences for a DataObject via the unified
 * `/v2/references?kind=hdf` surface.
 *
 * PLUGIN-REF-HANDLER-FE-REPOINT: migrated from the plugin-specific
 * `/v2/data-objects/{appId}/hdf-references` path to the unified
 * `GET /v2/references?kind=hdf&dataObjectAppId={appId}` endpoint now that
 * the `hdf` ReferenceKindHandler is installed (merged in bfab5f04b).
 *
 * The per-kind `payload` map carries: hdfContainerAppId, datasetPath,
 * description — mapped back to the typed `HdfReferenceIO` interface.
 *
 * Graceful degradation: treats HTTP 404 (DataObject not found) and HTTP 400
 * (kind handler not installed / plugin disabled) as an empty list so the pane
 * renders cleanly on instances without the HDF plugin.
 *
 * Note: `HdfReferencesPane.vue` manages its own inline fetch for create/delete
 * operations and does not import this composable. Updating this composable
 * does not change that pane's runtime behaviour — it is included here for API
 * consistency and future consumers.
 */

export interface HdfReferenceIO {
  appId: string;
  hdfContainerAppId?: string;
  datasetPath?: string;
  description?: string;
}

interface ReferenceV2IO {
  appId: string;
  kind: string;
  payload: Record<string, unknown>;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

function toHdfReferenceIO(r: ReferenceV2IO): HdfReferenceIO {
  const p = r.payload;
  return {
    appId: r.appId,
    hdfContainerAppId: (p.hdfContainerAppId as string | undefined) ?? undefined,
    datasetPath: (p.datasetPath as string | undefined) ?? undefined,
    description: (p.description as string | undefined) ?? undefined,
  };
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

    const url =
      `${v2BaseUrl()}/v2/references` +
      `?kind=hdf&dataObjectAppId=${encodeURIComponent(dataObjectAppId)}`;

    try {
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404 || response.status === 400) {
        // 404 = DataObject not found; 400 = HDF kind handler not installed
        // (shepard.hdf.enabled=false). Treat both as empty list.
        references.value = [];
        return;
      }
      if (!response.ok) {
        const bodyText = await response.text().catch(() => "");
        fetchError.value = `Failed to fetch HDF references (HTTP ${response.status}): ${bodyText.slice(0, 200)}`;
        handleError(fetchError.value, "listHdfReferences");
        return;
      }
      const raw = (await response.json()) as ReferenceV2IO[];
      references.value = raw.map(toHdfReferenceIO);
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
