import StructuredDataService from "@/services/structuredDataService";
import { handleError } from "@/utils/error-handling";
import {
  ContainerSearchParamsQueryTypeEnum,
  type ResponseError,
  type StructuredDataContainer,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";
import { useInlineSearch } from "./InlineSearchContainers";

export function useSearchStructuredDataContainers(text: Ref<string>) {
  const { searchResults, searchQuery } = useInlineSearch(
    text,
    ContainerSearchParamsQueryTypeEnum.Structureddata,
  );
  const resultSet = ref<StructuredDataContainer[]>([]);
  const totalResults = ref(0);

  function handleResponse() {
    resultSet.value = [];
    if (searchResults.value?.structuredDataContainers) {
      totalResults.value = searchResults.value.structuredDataContainers.length;
      searchResults.value.structuredDataContainers
        .slice(0, 10)
        .forEach(result => {
          if (result.id) {
            retrieveById(result.id);
          }
        });
    } else {
      totalResults.value = 0;
    }
  }

  function retrieveById(id: number) {
    StructuredDataService.getStructuredDataContainer({
      structureddataContainerId: id,
    })
      .then(response => {
        resultSet.value = [...resultSet.value, response];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching structured data container");
      });
  }

  watch(searchResults, handleResponse);

  return { resultSet, totalResults, searchQuery };
}
