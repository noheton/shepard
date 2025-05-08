import { type UserGroup, UserGroupApi } from "@dlr-shepard/backend-client";

export function useFetchUserGroups() {
  const userGroups = ref<UserGroup[]>([]);
  const isLoading = ref<boolean>(false);

  async function fetchUserGroups() {
    isLoading.value = true;
    createApiInstance(UserGroupApi)
      .getAllUserGroups({ orderBy: "createdAt", orderDesc: false })
      .then(response => {
        userGroups.value = response;
        isLoading.value = false;
      })
      .catch(error => {
        userGroups.value = [];
        isLoading.value = false;
        handleError(error, "getAllUserGroups");
      });
  }

  fetchUserGroups();

  return { userGroups, isLoading };
}
