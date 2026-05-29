/**
 * REF-UNIFIED-TABLE-FR1B — fetches the FR1b singleton FileReferences
 * attached to a DataObject so the unified data-references table can
 * render them alongside FR1a bundles + timeseries + structured-data.
 *
 * Backed by the additive v2 endpoint
 * `GET /v2/files/by-data-object/{dataObjectAppId}` (J1e +
 * REF-UNIFIED-TABLE-FR1B). The upstream v1 list endpoint
 * `GET /shepard/api/collections/{collectionId}/dataObjects/{dataObjectId}/fileReferences`
 * stays byte-frozen and returns only FR1a bundle shapes; this composable's
 * additive read complements it.
 *
 * Wire shape mirrors `FileReferenceV2IO`:
 *   {
 *     appId: string;          // UUID v7 of the singleton FileReference
 *     name: string;           // human-readable display name
 *     dataObjectId: number;   // numeric id of the parent DO (OGM)
 *     createdAt: string;      // ISO timestamp
 *     createdBy: string;
 *     type: "FileReference";  // shared with FR1a per upstream convention
 *     file: {                 // embedded ShepardFile metadata
 *       filename: string;     // original upload filename (used for .ipynb detection)
 *       fileSize: number | null;
 *       md5: string;
 *       oid: string;
 *     } | null;
 *   }
 */

import type { ShepardFile } from "@dlr-shepard/backend-client";

export interface SingletonFileReferenceIO {
  appId: string;
  name: string;
  dataObjectId?: number;
  createdAt: string;
  createdBy: string;
  type?: string;
  file: ShepardFile | null;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

export function useFetchSingletonFileReferences(
  dataObjectAppId: Ref<string | undefined> | string | undefined,
) {
  const references = ref<SingletonFileReferenceIO[]>([]);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    const appId = isRef(dataObjectAppId) ? dataObjectAppId.value : dataObjectAppId;
    if (!appId) {
      references.value = [];
      return;
    }
    isLoading.value = true;
    error.value = null;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/files/by-data-object/${encodeURIComponent(appId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) {
        // 404 (DO missing) and 403 (no permission) treated as empty.
        if (response.status === 404 || response.status === 403) {
          references.value = [];
          return;
        }
        throw new Error(`HTTP ${response.status}`);
      }
      references.value = (await response.json()) as SingletonFileReferenceIO[];
    } catch (e) {
      error.value = "Failed to load singleton file references";
      handleError(e, "fetching singleton file references");
      references.value = [];
    } finally {
      isLoading.value = false;
    }
  }

  if (isRef(dataObjectAppId)) {
    watch(dataObjectAppId, () => refresh(), { immediate: true });
  } else {
    refresh();
  }

  return { references, isLoading, error, refresh };
}
