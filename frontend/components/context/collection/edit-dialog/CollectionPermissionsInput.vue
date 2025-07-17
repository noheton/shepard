<script setup lang="ts">
import type { UpdatedPermissions } from "./collectionEditTypes";

interface CollectionPermissionsInputProps {
  collectionId: number;
}

const props = defineProps<CollectionPermissionsInputProps>();

const updatedPermissions = defineModel<UpdatedPermissions>("permissions", {
  required: true,
});

const { collectionPermissions } = useFetchCollectionPermissions(
  props.collectionId,
);

watch(collectionPermissions, () => {
  if (collectionPermissions.value) {
    updatedPermissions.value = collectionPermissions.value;
  }
});
</script>

<template>
  <PermissionTypeInput
    v-if="updatedPermissions"
    v-model:permission-type="updatedPermissions.permissionType"
  />
</template>
