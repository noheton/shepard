/**
 * MISSING-aas-ui Slice 2 — composable wrapping GET /v2/aas/shells/{appId}
 * and GET /v2/aas/shells/{appId}/submodels.
 *
 * Accepts a bare Collection appId as the path parameter (IDTA-01002-3-2 §4.3
 * base64url-encoded form also accepted by the backend, but the frontend always
 * passes the bare appId extracted via shellIdToAppId()).
 *
 * Handles:
 *   - 501 "AAS integration disabled" state → `isDisabled = true`
 *   - 404 "Shell not found or no read access" → `isNotFound = true`
 *   - PagedResponseIO pagination for submodels
 *   - Bearer auth from useAuth()
 *
 * Backend resources: AasShellsRest — GET /v2/aas/shells/{aasId} (AAS1b single-Shell)
 *                    AasShellsRest — GET /v2/aas/shells/{aasId}/submodels (AAS1b submodel refs).
 */
import type { AasShellIO } from "~/composables/aas/useAasShells";

const DATAOBJECT_URN_PREFIX = "urn:shepard:dataobject:";

/** Extract the DataObject appId from an AAS Submodel reference key value. */
export function submodelRefToAppId(value: string): string {
  if (value.startsWith(DATAOBJECT_URN_PREFIX)) {
    return value.slice(DATAOBJECT_URN_PREFIX.length);
  }
  return value;
}

export interface AasSubmodelRefIO {
  type: string;
  keys: Array<{ type: string; value: string }>;
}

interface SubmodelsPage {
  items: AasSubmodelRefIO[];
  total: number;
  page: number;
  pageSize: number;
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
 * Fetch a single AAS Shell by Collection appId (GET /v2/aas/shells/{appId})
 * and lazily load its Submodel references (GET /v2/aas/shells/{appId}/submodels).
 *
 * `isDisabled` is true when the backend returns 501.
 * `isNotFound` is true when the backend returns 404.
 */
export function useAasShell(collectionAppId: string) {
  const shell = ref<AasShellIO | null>(null);
  const submodels = ref<AasSubmodelRefIO[]>([]);
  const submodelsTotal = ref(0);
  const submodelsPage = ref(0);
  const submodelsPageSize = ref(50);
  const isLoading = ref(false);
  const isSubmodelsLoading = ref(false);
  const isDisabled = ref(false);
  const isNotFound = ref(false);
  const error = ref<string | null>(null);

  async function fetchShell() {
    isLoading.value = true;
    error.value = null;
    isDisabled.value = false;
    isNotFound.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url = `${v2BaseUrl()}/v2/aas/shells/${encodeURIComponent(collectionAppId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 501) {
        isDisabled.value = true;
        return;
      }
      if (response.status === 404) {
        isNotFound.value = true;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      shell.value = await response.json();
    } catch (e) {
      error.value = "Failed to load AAS Shell";
      handleError(e, "fetching AAS Shell");
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchSubmodels() {
    isSubmodelsLoading.value = true;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url =
        `${v2BaseUrl()}/v2/aas/shells/${encodeURIComponent(collectionAppId)}/submodels` +
        `?page=${submodelsPage.value}&pageSize=${submodelsPageSize.value}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (!response.ok) return;
      const data: SubmodelsPage = await response.json();
      submodels.value = data.items;
      submodelsTotal.value = data.total;
    } catch (e) {
      handleError(e, "fetching AAS Shell submodels");
    } finally {
      isSubmodelsLoading.value = false;
    }
  }

  async function refresh() {
    await fetchShell();
    if (!isDisabled.value && !isNotFound.value && !error.value) {
      await fetchSubmodels();
    }
  }

  refresh();

  return {
    shell,
    submodels,
    submodelsTotal,
    submodelsPage,
    submodelsPageSize,
    isLoading,
    isSubmodelsLoading,
    isDisabled,
    isNotFound,
    error,
    refresh,
    fetchSubmodels,
  };
}
