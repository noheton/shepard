import { SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { readDataObjectAppId } from "~/utils/appId";

export interface DataObjectSearchResult {
  dataObjectName: string;
  /**
   * @deprecated Numeric Neo4j id — not exposed by v2 search; set to 0 when
   * the result comes from `GET /v2/search`. Use `dataObjectAppId` for
   * navigation and any appId-keyed v2 operation.
   */
  dataObjectId: number;
  /** UUID v7 — the stable cross-substrate identifier; use this for navigation. */
  dataObjectAppId: string | null;
  /**
   * @deprecated Owning collection numeric id — set only when falling back to
   * the v1 search path. Use `parentCollectionAppId` for navigation.
   */
  collectionId?: number;
  /**
   * AppId of the owning Collection — set by GET /v2/search.
   * Enables navigation without a secondary collection-resolver call.
   */
  parentCollectionAppId?: string | null;
}

/**
 * DataObject search composable.
 *
 * When `collectionAppId` (UUID v7) is provided the search is scoped to that
 * collection via `GET /v2/search?collectionAppId=…` (SEARCH-V2-3).
 * When only a numeric `collectionId` is available the composable falls back to
 * `POST /shepard/api/search` (V1-EXCEPTION — resolved once all callers carry
 * the collection appId).
 * When neither is provided the search runs globally via `GET /v2/search`.
 */
export function useDataObjectSearch(
  collectionId: number | undefined,
  searchString: Ref<string | undefined>,
  onSearchDone?: () => void,
  collectionAppId?: string | undefined,
) {
  const isLoading = ref<boolean>(false);
  const dataObjectSearchResults = ref<DataObjectSearchResult[]>([]);

  const v2Api = useV2ShepardApi(SearchApi);

  const searchDone = (callbackFn?: () => void) => {
    isLoading.value = false;
    if (callbackFn) {
      callbackFn();
    }
  };

  async function searchViaV2(query: string, scopeCollectionAppId?: string) {
    const result = await v2Api.value.searchV2({
      q: query,
      ...(scopeCollectionAppId ? { collectionAppId: scopeCollectionAppId } : {}),
    });
    result.items
      .filter(item => item.kind === "dataobject")
      .forEach(item => {
        if (
          !dataObjectSearchResults.value.some(
            existing => existing.dataObjectAppId === item.appId,
          )
        ) {
          dataObjectSearchResults.value.push({
            dataObjectId: 0,
            dataObjectName: item.name,
            dataObjectAppId: item.appId,
            parentCollectionAppId: item.parentCollectionAppId,
          });
        }
      });
  }

  async function searchViaV1(collectionIdParam: number, query: string) {
    // V1-EXCEPTION: caller only has a numeric collection id; no v2 collection-
    // scoped DO search is possible until the caller is updated to carry appId.
    // Tracked: SEARCH-V2-3 (remaining callers — PredecessorInput, ParentInput).
    let searchStringParam = "";
    if (isIntegerString(query)) {
      searchStringParam = createSearchQueryFromId(parseInt(query));
    } else {
      searchStringParam = createSearchQueryFromString(query);
    }
    const searchResponse = await useShepardApi(SearchApi).value.search({
      searchBody: {
        searchParams: { query: searchStringParam, queryType: "DataObject" },
        scopes: [{ collectionId: collectionIdParam, traversalRules: [] }],
      },
    });
    if (searchResponse.results) {
      const triples = searchResponse.resultSet ?? [];
      searchResponse.results.forEach((result, idx) => {
        if (
          !dataObjectSearchResults.value.some(
            existing => existing.dataObjectId === result.id,
          )
        ) {
          dataObjectSearchResults.value.push({
            dataObjectId: result.id,
            dataObjectName: result.name,
            dataObjectAppId: readDataObjectAppId(result),
            collectionId: triples[idx]?.collectionId,
          });
        }
      });
    }
  }

  async function searchDataObjectsByQuery(
    collectionIdParam: number | undefined,
    query: string,
    collectionAppIdParam?: string,
  ) {
    if (isLoading.value === true) return;
    isLoading.value = true;
    try {
      if (collectionAppIdParam) {
        await searchViaV2(query, collectionAppIdParam);
      } else if (collectionIdParam !== undefined) {
        await searchViaV1(collectionIdParam, query);
      } else {
        await searchViaV2(query);
      }
    } finally {
      searchDone(onSearchDone);
    }
  }

  function createSearchQueryFromString(query: string): string {
    return JSON.stringify({ property: "name", operator: "contains", value: query });
  }

  function createSearchQueryFromId(searchId: number): string {
    return JSON.stringify({ property: "id", operator: "eq", value: searchId });
  }

  function isIntegerString(value: string): boolean {
    return /^[+-]?\d+$/.test(value);
  }

  function resetResultList() {
    dataObjectSearchResults.value = [];
  }

  /**
   * Trigger a search. Returns the underlying Promise so callers that
   * compose multiple searches can observe completion / rejection.
   *
   * @param collectionIdParam  Override the closure-captured numeric id
   *                           (legacy callers only).
   * @param collectionAppIdParam  Override the closure-captured appId.
   */
  const startSearch = (
    collectionIdParam?: number,
    collectionAppIdParam?: string,
  ): Promise<void> => {
    if (!searchString.value) {
      resetResultList();
      return Promise.resolve();
    }
    const currCollectionId = collectionIdParam ?? collectionId;
    const currCollectionAppId = collectionAppIdParam ?? collectionAppId;
    return searchDataObjectsByQuery(currCollectionId, searchString.value, currCollectionAppId);
  };

  return {
    dataObjectSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
