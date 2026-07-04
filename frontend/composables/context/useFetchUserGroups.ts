import {
  useUserGroupsV2,
  type UserGroupV2,
} from "~/composables/context/useUserGroupsV2";

/**
 * V2-SWEEP-002-2 — lists user groups via the appId-keyed `/v2/user-groups`
 * surface (`UserGroupV2Rest`). Replaces the former
 * `useShepardApi(UserGroupApi).getAllUserGroups` v1 call.
 */
export function useFetchUserGroups() {
  const userGroups = ref<UserGroupV2[]>([]);
  const isLoading = ref<boolean>(false);

  const { listUserGroups } = useUserGroupsV2();

  async function fetchUserGroups() {
    isLoading.value = true;
    try {
      userGroups.value = await listUserGroups();
    } catch (error) {
      userGroups.value = [];
      handleError(error, "getAllUserGroups");
    } finally {
      isLoading.value = false;
    }
  }

  fetchUserGroups();

  onUserGroupUpdated(fetchUserGroups);

  return { userGroups, isLoading };
}
