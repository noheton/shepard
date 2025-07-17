<script setup lang="ts">
import type { UserGroup } from "@dlr-shepard/backend-client";
import { useFetchUserGroups } from "~/composables/context/useFetchUserGroups";

const { userGroups, isLoading } = useFetchUserGroups();

const emit = defineEmits<{
  (e: "select-user-group", userGroup: UserGroup): void;
}>();

const showCreateDialog = ref<boolean>(false);

const handleUserGroupCreated = (userGroup: UserGroup) => {
  handleUserGroupListUpdate();
  emit("select-user-group", userGroup);
};
</script>

<template>
  <ConfigurationPane
    title="User Groups"
    add-button-text="Add User Group"
    @show-create-dialog="showCreateDialog = true"
  >
    <template #create-dialog>
      <CreateUserGroupDialog
        v-if="showCreateDialog"
        v-model:show-dialog="showCreateDialog"
        @user-group-created="handleUserGroupCreated"
      />
    </template>
    <template #table>
      <UserGroupsTable
        :user-groups="userGroups"
        :loading="isLoading"
        @select-user-group="userGroup => emit('select-user-group', userGroup)"
      />
    </template>
  </ConfigurationPane>
</template>
