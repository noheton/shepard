import {
  GitCredentialsApi,
  type GitCredential,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../common/api/useV2ShepardApi";

export function useFetchGitCredentials() {
  const credentials = ref<GitCredential[]>([]);
  const isLoading = ref<boolean>(true);

  const api = useV2ShepardApi(GitCredentialsApi);

  async function refresh() {
    isLoading.value = true;
    try {
      const page = await api.value.listUserGitCredentials();
      credentials.value = (page.items as GitCredential[]) ?? [];
    } catch (error) {
      handleError(error, "fetching git credentials");
    } finally {
      isLoading.value = false;
    }
  }

  refresh();

  return { credentials, isLoading, refresh };
}
