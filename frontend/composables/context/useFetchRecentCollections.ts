import { CollectionsApi, type CollectionV2 } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const CLOSED_STATUS = "CLOSED";
const CLEANUP_STATUS = "PENDING_CLEANUP";

export function isClosedCollection(c: { status?: string | null }): boolean {
  return c.status?.toUpperCase() === CLOSED_STATUS;
}

export function isCleanupCollection(c: { status?: string | null }): boolean {
  return c.status?.toUpperCase() === CLEANUP_STATUS;
}

/**
 * Fetches up to 30 collections accessible to the current user, sorted by
 * most recently updated. Exposes a showClosed toggle so the caller can
 * include or exclude CLOSED collections in the filtered view.
 */
export function useFetchRecentCollections() {
  const v2Api = useV2ShepardApi(CollectionsApi);

  const allCollections = ref<CollectionV2[]>([]);
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
      const result = await v2Api.value.listCollections({ pageSize: 200 });
      const items = (result.items ?? []) as CollectionV2[];
      // Server returns unordered page; sort client-side by updatedAt desc.
      const sorted = items.slice().sort((a, b) => {
        const ta = a.updatedAt?.getTime() ?? 0;
        const tb = b.updatedAt?.getTime() ?? 0;
        return tb - ta;
      });
      allCollections.value = sorted.slice(0, 30);
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
