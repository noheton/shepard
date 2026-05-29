import {
  SearchApi,
  BasicCollectionAttributes,
  type Collection,
} from "@dlr-shepard/backend-client";
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
 * Fetches up to 6 collections accessible to the current user, sorted by
 * most recently updated. Exposes a showClosed toggle so the caller can
 * include or exclude CLOSED collections in the filtered view.
 *
 * PERF11: uses useAsyncData so the initial fetch is serialised into the
 * SSR payload — avoids a 300–600 ms client-side re-fetch on every navigation.
 * The key `recent-collections` is stable per session; `refresh()` is exposed
 * as `refetch` so post-mutation callers can invalidate.
 */
export function useFetchRecentCollections() {
  const searchApi = useShepardApi(SearchApi);
  const showClosed = ref(false);
  const error = ref<string | null>(null);

  const { data, pending, refresh } = useAsyncData(
    "recent-collections",
    async () => {
      error.value = null;
      try {
        const result = await searchApi.value.searchCollections({
          collectionSearchBody: { searchParams: { query: "" } },
          page: 0,
          size: 6,
          orderBy: BasicCollectionAttributes.UpdatedAt,
          orderDesc: true,
        });
        return result.results ?? [];
      } catch (e) {
        handleError(e, "fetching recent collections");
        error.value = "Could not load collections.";
        return [];
      }
    },
    { default: () => [] as Collection[] },
  );

  const allCollections = computed(() => data.value ?? []);
  const loading = computed(() => pending.value);

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
