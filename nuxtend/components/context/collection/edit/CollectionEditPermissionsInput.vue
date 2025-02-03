<script setup lang="ts">
import type { UpdatedPermissions } from "./collectionEditTypes";

interface CollectionEditPermissionsInputProps {
  collectionId: number;
  updatedPermissions: UpdatedPermissions;
  updatePermissions: (newValue: UpdatedPermissions) => void;
}

const props = defineProps<CollectionEditPermissionsInputProps>();

const { collectionPermissions } = useFetchCollectionPermissions(
  props.collectionId,
);

watch(collectionPermissions, () => {
  if (collectionPermissions.value) {
    props.updatePermissions({
      manager: collectionPermissions.value.manager,
      owner: collectionPermissions.value.owner,
      reader: collectionPermissions.value.reader,
      writer: collectionPermissions.value.writer,
      permissionType: collectionPermissions.value.permissionType,
      readerGroupIds: collectionPermissions.value.readerGroupIds,
      writerGroupIds: collectionPermissions.value.writerGroupIds,
    });
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
