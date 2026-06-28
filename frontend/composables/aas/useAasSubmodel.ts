/**
 * MISSING-aas-ui Slice 10 — composable fetching a DataObject in AAS Submodel shape.
 *
 * Uses GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 * (existing v2 endpoint — no new backend endpoint required).
 *
 * The Submodel IRI is derived as `urn:shepard:dataobject:{dataObjectAppId}`,
 * consistent with the IRI scheme used by the AAS Shell's submodel reference list
 * (GET /v2/aas/shells/{shellId}/submodels → keys[0].value).
 *
 * Handles:
 *   - 404 "DataObject not found or no read access" → `isNotFound = true`
 *   - Bearer auth from useAuth()
 */

const DATAOBJECT_URN_PREFIX = "urn:shepard:dataobject:";

/** Compute the AAS Submodel IRI from a DataObject appId. */
export function dataObjectAppIdToSubmodelIri(appId: string): string {
  return `${DATAOBJECT_URN_PREFIX}${appId}`;
}

export interface AasSubmodelDetailIO {
  /** AAS idShort — maps to DataObject.name */
  idShort: string;
  /** AAS Submodel IRI — urn:shepard:dataobject:{appId} */
  id: string;
  /** DataObject description (may be empty) */
  description: string;
  /** Raw DataObject appId for constructing links */
  appId: string;
  /** Parent Collection appId (= shellId) */
  collectionAppId: string;
}

function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

/**
 * Fetch a single AAS Submodel by Collection + DataObject appId.
 *
 * `isNotFound` is true when the backend returns 404 (DataObject absent or
 * caller lacks read access on the parent Collection).
 */
export function useAasSubmodel(collectionAppId: string, dataObjectAppId: string) {
  const submodel = ref<AasSubmodelDetailIO | null>(null);
  const isLoading = ref(false);
  const isNotFound = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    isNotFound.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url =
        `${v2BaseUrl()}/v2/collections/` +
        `${encodeURIComponent(collectionAppId)}/data-objects/` +
        `${encodeURIComponent(dataObjectAppId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 404) {
        isNotFound.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      submodel.value = {
        idShort: data.name ?? dataObjectAppId,
        id: dataObjectAppIdToSubmodelIri(dataObjectAppId),
        description: data.description ?? "",
        appId: dataObjectAppId,
        collectionAppId,
      };
    } catch (e) {
      error.value = "Failed to load Submodel";
      handleError(e, "fetching AAS Submodel");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { submodel, isLoading, isNotFound, error, refresh };
}
