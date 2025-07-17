<script setup lang="ts">
import type { UserGroup } from "@dlr-shepard/backend-client";
import { ConfigurationFragments } from "./configurationMenuItems";
import UserGroupDetailView from "./UserGroupDetailView.vue";
import UserGroupList from "./UserGroupList.vue";

const selectedUserGroup = ref<UserGroup | undefined>(undefined);
</script>

<template>
  <div :id="ConfigurationFragments.USER_GROUPS" class="d-flex flex-column">
    <UserGroupList
      v-if="!selectedUserGroup"
      @select-user-group="
        (userGroup: UserGroup) => (selectedUserGroup = userGroup)
      "
    />
    <UserGroupDetailView
      v-else
      :user-group="selectedUserGroup"
      @back="selectedUserGroup = undefined"
    />
  </div>
</template>
