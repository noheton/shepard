<script setup lang="ts">
import {
  PermissionType,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import { useEditCollectionPermissions } from "./useEditCollectionPermissions";
import UserAndGroupAutocomplete from "./UserAndGroupAutocomplete.vue";
import { UserRole } from "./UserRole";

interface EditPermissionsDialogProps {
  collectionId: number;
  isOwner?: boolean;
}

const props = defineProps<EditPermissionsDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref(true);

const selectedAdditionalUserRole = ref<UserRole | undefined>(undefined);
const selectedAdditionalUser = ref<User | undefined>(undefined);
const selectedAdditionalGroup = ref<UserGroup | undefined>(undefined);

const { collectionPermissions, owner } = useFetchCollectionPermissions(
  props.collectionId,
);

const { updatedPermissions, saveChanges } = useEditCollectionPermissions(
  props.collectionId,
  () => (showDialog.value = false),
  isValid,
);

watch(collectionPermissions, () => {
  if (collectionPermissions.value) {
    updatedPermissions.value = collectionPermissions.value;
  }
});

const onOwnerChange = (updatedOwner: User) => {
  if (updatedPermissions.value)
    updatedPermissions.value.owner = updatedOwner.username;
};
const onPermissionTypeChange = (updatedPermissionType: PermissionType) => {
  if (updatedPermissions.value)
    updatedPermissions.value.permissionType = updatedPermissionType;
};

const onAddPermission = () => {
  if (updatedPermissions.value && selectedAdditionalUserRole.value) {
    switch (selectedAdditionalUserRole.value) {
      case UserRole.manager: {
        if (selectedAdditionalUser.value)
          updatedPermissions.value.manager.push(
            selectedAdditionalUser.value.username,
          );
        break;
      }
      case UserRole.reader: {
        if (selectedAdditionalUser.value)
          updatedPermissions.value.reader.push(
            selectedAdditionalUser.value.username,
          );
        if (selectedAdditionalGroup.value)
          updatedPermissions.value.readerGroupIds = updatedPermissions.value
            .readerGroupIds
            ? updatedPermissions.value.readerGroupIds.concat(
                selectedAdditionalGroup.value.id,
              )
            : [selectedAdditionalGroup.value.id];

        break;
      }
      case UserRole.writer: {
        if (selectedAdditionalUser.value)
          updatedPermissions.value.writer.push(
            selectedAdditionalUser.value.username,
          );
        if (selectedAdditionalGroup.value)
          updatedPermissions.value.writerGroupIds = updatedPermissions.value
            .writerGroupIds
            ? updatedPermissions.value.writerGroupIds.concat(
                selectedAdditionalGroup.value.id,
              )
            : [selectedAdditionalGroup.value.id];
        break;
      }
    }
    resetAdditionalPermission();
  }
};
const resetAdditionalPermission = () => {
  selectedAdditionalGroup.value = undefined;
  selectedAdditionalUser.value = undefined;
  selectedAdditionalUserRole.value = undefined;
};
</script>

<template>
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Edit Permissions"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form
        v-if="collectionPermissions"
        ref="form"
        v-model="isValid"
        validate-on="invalid-input eager"
      >
        <v-row class="pt-8">
          <v-col class="text-semibold text-textbody1">
            General Permissions
            <Tooltip>
              <div>Public: Container is available for all.</div>
              <div>Public Readable: Container is readable for all.</div>
              <div>Private: Container has private permissions.</div>
            </Tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <v-select
              :model-value="collectionPermissions.permissionType"
              label="Permission Type"
              density="compact"
              :items="Object.values(PermissionType)"
              variant="outlined"
              @update:model-value="onPermissionTypeChange"
            />
          </v-col>
          <v-col>
            <OwnerAutocompleteInput
              v-if="owner"
              :model-value="owner"
              :is-owner="isOwner"
              @owner-change="onOwnerChange"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="text-semibold text-textbody1">
            Additional Permissions
            <Tooltip>
              <div>Reader: User can only read contents.</div>
              <div>Writer: User can read and change contents.</div>
              <div>Manager: User can give and change permissions.</div>
              <div>Owner: User has all rights.</div>
            </Tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <UserAndGroupAutocomplete
              @user-group-select="
                (selectedUserGroup: UserGroup) => {
                  selectedAdditionalGroup = selectedUserGroup;
                }
              "
              @user-select="
                (selectedUser: User) => {
                  selectedAdditionalUser = selectedUser;
                }
              "
            />
          </v-col>
          <v-col>
            <v-select
              v-model="selectedAdditionalUserRole"
              :items="Object.values(UserRole)"
              label="User or group role"
              variant="outlined"
              density="compact"
              color="primary"
              require
              hide-details
            />
          </v-col>
          <v-col cols="2">
            <v-btn
              prepend-icon="mdi-plus-circle"
              color="primary"
              variant="flat"
              @click="onAddPermission"
            >
              ADD
            </v-btn>
          </v-col>
        </v-row>
      </v-form>
    </template>
  </Dialog>
</template>
