import {
  GitReferenceApi,
  type GitReferenceIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchGitReferences(dataObjectAppId: string) {
  const gitReferences = ref<GitReferenceIO[]>([]);
  const isLoading = ref(false);

  function refresh() {
    isLoading.value = true;
    useV2ShepardApi(GitReferenceApi)
      .value.listGitReferences(dataObjectAppId)
      .then(result => {
        gitReferences.value = result;
      })
      .catch(error => {
        handleError(error, "listGitReferences");
      })
      .finally(() => {
        isLoading.value = false;
      });
  }

  refresh();

  return { gitReferences, isLoading, refresh };
}
