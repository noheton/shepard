import CollectionService from "@/services/collectionService";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import {
  SearchParamsQueryTypeEnum,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/shepard-client";
import { computed, ref, watch, type Ref } from "vue";

export function useSearchCollections(text: Ref<string>) {
  const resultSet = ref<Collection[]>([]);
  const totalResults = ref(0);
  const searchQuery = computed(() => {
    return JSON.stringify({
      OR: [
        {
          property: "name",
          value: text.value,
          operator: "contains",
        },
        {
          property: "createdBy",
          value: text.value,
          operator: "contains",
        },
        {
          property: "description",
          value: text.value,
          operator: "contains",
        },
        {
          property: "id",
          value: Number(text.value),
          operator: "eq",
        },
      ],
    });
  });

  function inlineSearch() {
    SearchService.search({
      searchBody: {
        scopes: [
          {
            traversalRules: [],
          },
        ],
        searchParams: {
          query: searchQuery.value,
          queryType: SearchParamsQueryTypeEnum.Collection,
        },
      },
    })
      .then(response => {
        resultSet.value = [];
        totalResults.value = response.resultSet?.length || 0;
        response.resultSet?.slice(0, 10).forEach(result => {
          if (result.collectionId) {
            retrieveCollectionById(result.collectionId);
          }
        });
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching search data");
      });
  }

  function retrieveCollectionById(collectionId: number) {
    CollectionService.getCollection({
      collectionId: collectionId,
    })
      .then(response => {
        resultSet.value = [...resultSet.value, response];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection");
      });
  }

  watch(text, () => {
    if (
      text.value.length != 0 &&
      (text.value.length >= 3 || !isNaN(Number(text.value)))
    ) {
      inlineSearch();
    } else {
      resultSet.value = [];
    }
  });

  return { resultSet, totalResults, searchQuery };
}
