import {
  GitCredentialsApi,
  type GitCredentialIO,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchGitCredentials() {
  const credentials = ref<GitCredentialIO[]>([]);
  const isLoading = ref<boolean>(true);

  const api = useV2ShepardApi(GitCredentialsApi);

  async function refresh() {
    isLoading.value = true;
    try {
      credentials.value = await api.value.listCredentials();
    } catch (error) {
      handleError(error, "fetching git credentials");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { credentials, isLoading, refresh };
}
