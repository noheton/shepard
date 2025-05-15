import { UserApi, type User } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchUserProfile() {
  const user = ref<User>();
  const isLoading = ref<boolean>(false);

  function fetchUserProfile() {
    isLoading.value = true;
    useShepardApi(UserApi)
      .value.getCurrentUser()
      .then(response => {
        user.value = response;
        isLoading.value = false;
      })
      .catch(error => {
        isLoading.value = false;
        handleError(error, "fetching user profile");
      });
  }

  fetchUserProfile();

  return { user, isLoading };
}
