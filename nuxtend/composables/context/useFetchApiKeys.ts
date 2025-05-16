import {
  type ApiKey,
  ApikeyApi,
  type ResponseError,
  type User,
  UserApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export function useFetchApiKeys() {
  const apikeyApi = useShepardApi(ApikeyApi);
  const userApi = useShepardApi(UserApi);

  const user = ref<User>();
  const apiKeys = ref<ApiKey[]>();
  const isLoading = ref<boolean>(true);

  async function fetchApiKeys() {
    try {
      if (!user.value) {
        user.value = await userApi.value.getCurrentUser();
      }
      apiKeys.value = await apikeyApi.value.getAllApiKeys({
        username: user.value.username,
      });
      apiKeys.value.sort(
        (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
      );
      isLoading.value = false;
    } catch (e) {
      handleError(e as ResponseError, "fetching api keys");
    }
  }

  fetchApiKeys();

  return { apiKeys, user, fetchApiKeys, isLoading };
}
