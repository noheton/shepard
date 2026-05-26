/**
 * PROV1k — fetch typed predecessor summary list for a DataObject.
 *
 * Calls GET /v2/collections/{collectionAppId}/data-objects/{dataObjectAppId}
 * and returns the `typedPredecessorSummaries` array from the DataObjectDetail
 * response. This is a v2-only field not present in the v1 response used by
 * useFetchDataObject.
 *
 * The composable is best-effort: when the v2 endpoint is unavailable (e.g.
 * older backend, 404), it returns an empty array and logs silently.
 */

export interface TypedPredecessorSummary {
  predecessorAppId: string;
  predecessorId: number;
  predecessorName: string;
  predecessorStatus: string | null;
  relationshipType: string;
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
 * Fetch the `typedPredecessorSummaries` from the v2 DataObject detail endpoint.
 *
 * @param collectionAppId UUID v7 of the collection
 * @param dataObjectAppId UUID v7 of the DataObject
 */
export function useFetchTypedPredecessors(
  collectionAppId: string | null | undefined,
  dataObjectAppId: string | null | undefined,
) {
  const typedPredecessors = ref<TypedPredecessorSummary[]>([]);
  const isLoading = ref(false);

  async function fetchTypedPredecessors() {
    if (!collectionAppId || !dataObjectAppId) return;

    isLoading.value = true;
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      isLoading.value = false;
      return;
    }

    try {
      const url = `${v2BaseUrl()}/v2/collections/${encodeURIComponent(collectionAppId)}/data-objects/${encodeURIComponent(dataObjectAppId)}`;
      const response = await fetch(url, {
        headers: {
          Authorization: `Bearer ${accessToken}`,
          Accept: "application/json",
        },
      });
      if (response.ok) {
        const detail = (await response.json()) as {
          typedPredecessorSummaries?: TypedPredecessorSummary[];
        };
        typedPredecessors.value = detail.typedPredecessorSummaries ?? [];
      }
      // Best-effort: silently ignore 404 / non-ok responses
    } catch {
      // Best-effort: network errors silently yield empty list
    } finally {
      isLoading.value = false;
    }
  }

  fetchTypedPredecessors();

  onDataObjectUpdated(fetchTypedPredecessors);

  return { typedPredecessors, isLoading };
}
