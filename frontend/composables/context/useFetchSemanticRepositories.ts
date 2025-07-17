import {
  SemanticRepositoryApi,
  type SemanticRepository,
} from "@dlr-shepard/backend-client";
import { onSemanticRepositoriesUpdated } from "~/utils/resourceUpdateBus";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchSemanticRepositories() {
  const repositories = ref<SemanticRepository[]>([]);
  const isLoading = ref<boolean>(false);

  async function fetchSemanticRepositories() {
    isLoading.value = true;
    useShepardApi(SemanticRepositoryApi)
      .value.getAllSemanticRepositories({ orderBy: "name", orderDesc: false })
      .then(response => {
        repositories.value = response;
        isLoading.value = false;
      })
      .catch(error => {
        repositories.value = [];
        isLoading.value = false;
        handleError(error, "getAllSemanticRepositories");
      });
  }

  fetchSemanticRepositories();

  onSemanticRepositoriesUpdated(fetchSemanticRepositories);

  return { repositories, isLoading };
}
