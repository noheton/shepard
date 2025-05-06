import { UserApi, type User } from "@dlr-shepard/backend-client";

export function useFetchUserProfile() {
  const user = ref<User>();
  const isLoading = ref<boolean>(false);

  function fetchUserProfile() {
    isLoading.value = true;
    createApiInstance(UserApi)
      .getCurrentUser()
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
