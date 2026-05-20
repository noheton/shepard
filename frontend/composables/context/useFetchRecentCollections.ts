import { CollectionApi, DataObjectAttributes, type Collection } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

const CLOSED_STATUS = "CLOSED";
const CLEANUP_STATUS = "PENDING_CLEANUP";

export function isClosedCollection(c: Collection): boolean {
  return c.status?.toUpperCase() === CLOSED_STATUS;
}

export function isCleanupCollection(c: Collection): boolean {
  return c.status?.toUpperCase() === CLEANUP_STATUS;
}

/**
 * Fetches up to 30 collections accessible to the current user, sorted by
 * most recently updated. Exposes a showClosed toggle so the caller can
 * include or exclude CLOSED collections in the filtered view.
 */
export function useFetchRecentCollections() {
  const collectionApi = useShepardApi(CollectionApi);

  const allCollections = ref<Collection[]>([]);
  const loading = ref(true);
  const error = ref<string | null>(null);
  const showClosed = ref(false);

  async function fetch() {
    // Skip SSR — backendApiUrl is empty on the server side; this is
    // personalised data that requires auth and must load client-side.
    if (!import.meta.client) return;
    loading.value = true;
    error.value = null;
    try {
      const results = await collectionApi.value.getAllCollections({
        page: 0,
        size: 30,
        orderBy: DataObjectAttributes.UpdatedAt,
        orderDesc: true,
      });
      allCollections.value = results;
    } catch (e) {
      handleError(e, "fetching recent collections");
      error.value = "Could not load collections.";
    } finally {
      loading.value = false;
    }
  }

  fetch();

  const hasClosedCollections = computed(() =>
    allCollections.value.some(isClosedCollection),
  );

  const filteredCollections = computed(() =>
    showClosed.value
      ? allCollections.value
      : allCollections.value.filter(c => !isClosedCollection(c)),
  );

  return {
    collections: filteredCollections,
    allCollections,
    hasClosedCollections,
    showClosed,
    loading,
    error,
    refetch: fetch,
  };
}
