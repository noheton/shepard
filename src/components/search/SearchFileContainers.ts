import FileService from "@/services/fileService";
import { handleError } from "@/utils/error-handling";
import {
  ContainerSearchParamsQueryTypeEnum,
  type FileContainer,
  type ResponseError,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";
import { useInlineSearch } from "./InlineSearchContainers";

export function useSearchFileContainers(text: Ref<string>) {
  const { searchResults, searchQuery } = useInlineSearch(
    text,
    ContainerSearchParamsQueryTypeEnum.File,
  );
  const resultSet = ref<FileContainer[]>([]);
  const totalResults = ref(0);

  function handleResponse() {
    resultSet.value = [];
    if (searchResults.value?.fileContainers) {
      totalResults.value = searchResults.value.fileContainers.length;
      searchResults.value.fileContainers.slice(0, 10).forEach(result => {
        if (result.id) {
          retrieveById(result.id);
        }
      });
    } else {
      totalResults.value = 0;
    }
  }

  function retrieveById(id: number) {
    FileService.getFileContainer({
      fileContainerId: id,
    })
      .then(response => {
        resultSet.value = [...resultSet.value, response];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching file container");
      });
  }

  watch(searchResults, handleResponse);

  return { resultSet, totalResults, searchQuery };
}
