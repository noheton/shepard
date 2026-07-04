/**
 * MISSING-aas-ui Slice 1 — composable wrapping GET /v2/aas/shells.
 *
 * Raw-fetch pattern (no generated client for the AAS plugin surface yet;
 * follows the same shape as composables/context/useProject.ts).
 *
 * Handles:
 *   - 501 "AAS integration disabled" state → `isDisabled = true`
 *   - PagedResponseIO pagination envelope
 *   - Bearer auth from useAuth()
 *
 * Backend resource: AasShellsRest — GET /v2/aas/shells (AAS1a).
 */

export interface AasShellIO {
  id: string; // urn:shepard:collection:{appId}
  idShort: string;
  assetInformation: {
    assetKind: string;
    globalAssetId: string;
  };
  description?: Array<{ language: string; text: string }>;
  submodels: Array<{
    type: string;
    keys: Array<{ type: string; value: string }>;
  }>;
}

interface AasShellsPage {
  items: AasShellIO[];
  total: number;
  page: number;
  pageSize: number;
}

const COLLECTION_URN_PREFIX = "urn:shepard:collection:";

/** Extract the Collection appId from an AAS Shell IRI. */
export function shellIdToAppId(shellId: string): string {
  if (shellId.startsWith(COLLECTION_URN_PREFIX)) {
    return shellId.slice(COLLECTION_URN_PREFIX.length);
  }
  return shellId;
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
 * List AAS Shells (GET /v2/aas/shells) with page-offset pagination.
 *
 * `isDisabled` is true when the backend returns 501 (AAS integration
 * turned off via instance admin; enable via PATCH /v2/admin/aas/config).
 */
export function useAasShells() {
  const shells = ref<AasShellIO[]>([]);
  const total = ref(0);
  const page = ref(0);
  const pageSize = ref(50);
  const isLoading = ref(false);
  const isDisabled = ref(false);
  const error = ref<string | null>(null);

  async function refresh() {
    isLoading.value = true;
    error.value = null;
    isDisabled.value = false;
    try {
      const { data: session } = useAuth();
      const accessToken = session.value?.accessToken;
      const url =
        `${v2BaseUrl()}/v2/aas/shells?page=${page.value}&pageSize=${pageSize.value}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.status === 501) {
        isDisabled.value = true;
        shells.value = [];
        total.value = 0;
        return;
      }
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data: AasShellsPage = await response.json();
      shells.value = data.items;
      total.value = data.total;
    } catch (e) {
      error.value = "Failed to load AAS Shells";
      handleError(e, "fetching AAS Shells");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { shells, total, page, pageSize, isLoading, isDisabled, error, refresh };
}
