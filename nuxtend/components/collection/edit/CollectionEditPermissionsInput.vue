<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";
import type { UpdatedPermissions } from "./collectionEditTypes";

interface CollectionEditDialogProps {
  collectionId: number;
  updatedPermissions: UpdatedPermissions;
  updatePermissions: (newValue: UpdatedPermissions) => void;
}

const props = defineProps<CollectionEditDialogProps>();

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
  <v-row class="pb-4">
    <v-select
      v-if="!!updatedPermissions"
      :model-value="updatedPermissions.permissionType"
      :items="Object.values(PermissionType)"
      label="Permissions*"
      variant="outlined"
      density="compact"
      require
      hide-details
      @update:model-value="
        newPermissionType => {
          if (!!updatedPermissions)
            updatePermissions({
              ...updatedPermissions,
              permissionType: newPermissionType,
            });
        }
      "
    >
      <template #item="{ props: listItemProps, item }">
        <v-list-item
          v-bind="listItemProps"
          :title="
            item.value === PermissionType.PublicReadable
              ? 'Public Readable'
              : item.value
          "
        />
      </template>
      <template #selection="{ item }">
        {{
          item.value === PermissionType.PublicReadable
            ? "Public Readable"
            : item.value
        }}
      </template>
    </v-select>
  </v-row>
</template>
