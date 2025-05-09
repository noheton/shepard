<script setup lang="ts">
import { ConfigurationFragments } from "./configurationMenuItems";
import UserGroupDetailView from "./UserGroupDetailView.vue";
import UserGroupList from "./UserGroupList.vue";

const selectedUserGroupId = ref<number | undefined>(undefined);
</script>

<template>
  <div :id="ConfigurationFragments.USER_GROUPS" class="d-flex flex-column">
    <UserGroupList
      v-if="!selectedUserGroupId"
      @select-user-group="
        (userGroupId: number) => (selectedUserGroupId = userGroupId)
      "
    />
    <UserGroupDetailView
      v-else
      :user-group-id="selectedUserGroupId"
      @back="selectedUserGroupId = undefined"
    />
  </div>
</template>
