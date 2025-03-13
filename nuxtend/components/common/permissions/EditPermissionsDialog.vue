<script setup lang="ts">
import { PermissionType, type User } from "@dlr-shepard/backend-client";
import { useEditCollectionPermissions } from "./useEditCollectionPermissions";

interface EditPermissionsDialogProps {
  collectionId: number;
  isOwner?: boolean;
}

const _props = defineProps<EditPermissionsDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref(true);

const { collectionPermissions, owner } = useFetchCollectionPermissions(
  _props.collectionId,
);

const { updatedPermissions, saveChanges } = useEditCollectionPermissions(
  _props.collectionId,
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
              :items="Object.values(PermissionType)"
              variant="outlined"
              density="comfortable"
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
      </v-form>
    </template>
  </Dialog>
</template>
