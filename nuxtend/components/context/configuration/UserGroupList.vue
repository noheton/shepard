<script setup lang="ts">
import { useFetchUserGroups } from "~/composables/context/useFetchUserGroups";

const { userGroups, isLoading } = useFetchUserGroups();

const emit = defineEmits<{
  (e: "select-user-group", userGroupId: number): void;
}>();

const showCreateDialog = ref<boolean>(false);

const handleUserGroupCreated = (userGroupId: number) => {
  handleUserGroupListUpdate();
  emit("select-user-group", userGroupId);
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
        @select-user-group="
          userGroupId => emit('select-user-group', userGroupId)
        "
      />
    </template>
  </ConfigurationPane>
</template>
