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
  const showClosed = ref(false);

  const { data, pending: loading, error: rawError, refresh } = useAsyncData(
    "recent-collections",
    () => collectionApi.value.getAllCollections({
      page: 0,
      size: 30,
      orderBy: DataObjectAttributes.UpdatedAt,
      orderDesc: true,
    }),
    { server: false, default: () => [] as Collection[] },
  );

  const allCollections = computed<Collection[]>(() => data.value ?? []);

  const error = computed<string | null>(() =>
    rawError.value ? "Could not load collections." : null,
  );

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
    refetch: refresh,
  };
}
