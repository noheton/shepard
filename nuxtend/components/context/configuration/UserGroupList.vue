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
  <div class="d-flex align-center ga-4">
    <h4 class="text-h4">User Groups</h4>
  </div>

  <div class="d-flex justify-end pt-8">
    <v-btn
      class="bg-primary text-canvas"
      variant="flat"
      :style="{ marginTop: '3px' }"
      @click="showCreateDialog = true"
    >
      <template #prepend>
        <v-icon icon="mdi-plus-circle" color="canvas" />
      </template>
      Add User Group
    </v-btn>
    <CreateUserGroupDialog
      v-if="showCreateDialog"
      v-model:show-dialog="showCreateDialog"
      @user-group-created="handleUserGroupCreated"
    />
  </div>

  <UserGroupsTable
    :user-groups="userGroups"
    :loading="isLoading"
    @select-user-group="userGroupId => emit('select-user-group', userGroupId)"
  />
</template>
