import {
  BasicCollectionAttributes,
  SearchApi,
  type Collection,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * Fetches up to 6 collections accessible to the current user, sorted
 * by most recently updated (updatedAt desc). Used by the personal
 * landing page digest.
 */
export function useFetchRecentCollections() {
  const collections = ref<Collection[]>([]);
  const loading = ref(true);
  const error = ref<string | null>(null);

  const searchApi = useShepardApi(SearchApi);

  async function fetch() {
    loading.value = true;
    error.value = null;
    try {
      const response = await searchApi.value.searchCollections({
        collectionSearchBody: { searchParams: { query: "" } },
        page: 0,
        size: 6,
        orderBy: BasicCollectionAttributes.UpdatedAt,
        orderDesc: true,
      });
      collections.value = response.results;
    } catch (e) {
      handleError(e, "fetching recent collections");
      error.value = "Could not load collections.";
    } finally {
      loading.value = false;
    }
  }

  fetch();

  return { collections, loading, error, refetch: fetch };
}
