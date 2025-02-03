<script setup lang="ts">
import type { UpdatedPermissions } from "./collectionEditTypes";

interface CollectionPermissionsInputProps {
  collectionId: number;
  updatedPermissions: UpdatedPermissions;
  updatePermissions: (newValue: UpdatedPermissions) => void;
}

const props = defineProps<CollectionPermissionsInputProps>();

const { collectionPermissions } = useFetchCollectionPermissions(
  props.collectionId,
);

watch(collectionPermissions, () => {
  if (collectionPermissions.value) {
    props.updatePermissions(collectionPermissions.value);
  }
});
</script>

<template>
  <PermissionTypeInput
    v-if="updatedPermissions"
    :permission-type="updatedPermissions.permissionType"
    :update-permission-type="
      newPermissionType => {
        if (!!updatedPermissions)
          updatePermissions({
            ...updatedPermissions,
            permissionType: newPermissionType,
          });
      }
    "
  />
</template>
