/**
 * UX-PROV1 — fetch the predecessor chain for a DataObject.
 *
 * Calls GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}/predecessor-chain?depth=N
 * (ANC-1 endpoint). Returns an ordered array of DataObjectSummary items from the
 * current DataObject back toward the root, oldest-last.
 *
 * The composable is best-effort: when the endpoint is unavailable (e.g. 404,
 * network error), it returns an empty array and sets error = true.
 */

export interface DataObjectChainItem {
  appId: string;
  id: number;
  name: string;
  status: string | null;
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
 * Fetch the predecessor chain for a DataObject.
 *
 * @param collectionAppId  UUID v7 of the parent collection
 * @param dataObjectAppId  UUID v7 of the DataObject
 * @param depth            maximum chain depth (default 10)
 */
export function useFetchPredecessorChain(
  collectionAppId: string | null | undefined,
  dataObjectAppId: string | null | undefined,
  depth = 10,
) {
  const chain = ref<DataObjectChainItem[]>([]);
  const isLoading = ref(false);
  const hasError = ref(false);

  async function fetchChain() {
    if (!collectionAppId || !dataObjectAppId) return;

    isLoading.value = true;
    hasError.value = false;
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    try {
      const url =
        `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}` +
        `/data-objects/${encodeURIComponent(dataObjectAppId)}` +
        `/predecessor-chain?depth=${depth}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.ok) {
        const items = (await response.json()) as DataObjectChainItem[];
        chain.value = Array.isArray(items) ? items : [];
      } else {
        chain.value = [];
        if (response.status !== 404) hasError.value = true;
      }
    } catch {
      chain.value = [];
      hasError.value = true;
    } finally {
      isLoading.value = false;
    }
  }

  fetchChain();

  return { chain, isLoading, hasError, reload: fetchChain };
}
