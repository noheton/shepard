import {
  SemanticRepositoryApi,
  type SemanticRepository,
} from "@dlr-shepard/backend-client";

export function useFetchSemanticRepositories() {
  const repositories = ref<SemanticRepository[]>([]);

  async function fetchSemanticRepositories() {
    createApiInstance(SemanticRepositoryApi)
      .getAllSemanticRepositories({ orderBy: "name", orderDesc: false })
      .then(response => {
        repositories.value = response;
      })
      .catch(error => {
        repositories.value = [];
        handleError(error, "getAllSemanticRepositories");
      });
  }

  fetchSemanticRepositories();

  return { repositories };
}
