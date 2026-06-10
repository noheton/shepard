<script setup lang="ts">
import { ConfigurationFragments } from "./configurationMenuItems";
import UserGroupDetailView from "./UserGroupDetailView.vue";
import UserGroupList from "./UserGroupList.vue";
import type { UserGroupV2 } from "~/composables/context/useUserGroupsV2";

const selectedUserGroup = ref<UserGroupV2 | undefined>(undefined);
</script>

<template>
  <div :id="ConfigurationFragments.USER_GROUPS" class="d-flex flex-column">
    <UserGroupList
      v-if="!selectedUserGroup"
      @select-user-group="
        (userGroup: UserGroupV2) => (selectedUserGroup = userGroup)
      "
    />
    <UserGroupDetailView
      v-else
      :user-group="selectedUserGroup"
      @back="selectedUserGroup = undefined"
    />
  </div>
</template>
