import {
  SemanticRepositoryApi,
  type SemanticRepository,
} from "@dlr-shepard/backend-client";

export function useFetchSemanticRepositories() {
  const repositories = ref<SemanticRepository[]>([]);
  const isLoading = ref<boolean>(false);

  async function fetchSemanticRepositories() {
    isLoading.value = true;
    createApiInstance(SemanticRepositoryApi)
      .getAllSemanticRepositories({ orderBy: "name", orderDesc: false })
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

  return { repositories, isLoading };
}
