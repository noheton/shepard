import { type UserGroup, UserGroupApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export function useFetchUserGroups() {
  const userGroups = ref<UserGroup[]>([]);
  const isLoading = ref<boolean>(false);

  async function fetchUserGroups() {
    isLoading.value = true;
    useShepardApi(UserGroupApi)
      .value.getAllUserGroups({ orderDesc: false })
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

  onUserGroupUpdated(fetchUserGroups);

  return { userGroups, isLoading };
}
