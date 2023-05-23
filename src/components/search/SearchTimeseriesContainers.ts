import TimeseriesService from "@/services/timeseriesService";
import { handleError } from "@/utils/error-handling";
import {
  ContainerSearchParamsQueryTypeEnum,
  type ResponseError,
  type TimeseriesContainer,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";
import { useInlineSearch } from "./InlineSearchContainers";

export function useSearchTimeseriesContainers(text: Ref<string>) {
  const { searchResults, searchQuery } = useInlineSearch(
    text,
    ContainerSearchParamsQueryTypeEnum.Timeseries,
  );
  const resultSet = ref<TimeseriesContainer[]>([]);
  const totalResults = ref(0);

  function handleResponse() {
    resultSet.value = [];
    if (searchResults.value?.timeseriesContainers) {
      totalResults.value = searchResults.value.timeseriesContainers.length;
      searchResults.value.timeseriesContainers.slice(0, 10).forEach(result => {
        if (result.id) {
          retrieveById(result.id);
        }
      });
    } else {
      totalResults.value = 0;
    }
  }

  function retrieveById(id: number) {
    TimeseriesService.getTimeseriesContainer({
      timeseriesContainerId: id,
    })
      .then(response => {
        resultSet.value = [...resultSet.value, response];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching timeseries container");
      });
  }

  watch(searchResults, handleResponse);

  return { resultSet, totalResults, searchQuery };
}
