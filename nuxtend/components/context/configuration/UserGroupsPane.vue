<script setup lang="ts">
import { useFetchUserGroups } from "~/composables/context/useFetchUserGroups";
import { ConfigurationFragments } from "./configurationMenuItems";

const { userGroups, isLoading } = useFetchUserGroups();
</script>

<template>
  <div :id="ConfigurationFragments.USER_GROUPS" class="d-flex flex-column">
    <div class="d-flex align-center ga-4">
      <h4 class="text-h4">User Groups</h4>
    </div>

    <UserGroupsTable :user-groups="userGroups" :loading="isLoading" />
  </div>
</template>
