<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";

interface CollectionEditDialogProps {
  permissionType: PermissionType;
  updatePermissionType: (newValue: PermissionType) => void;
}

defineProps<CollectionEditDialogProps>();
</script>

<template>
  <v-row class="pb-4">
    <v-select
      :model-value="permissionType"
      :items="Object.values(PermissionType)"
      label="Permissions*"
      variant="outlined"
      density="compact"
      require
      hide-details
      @update:model-value="
        newPermissionType => updatePermissionType(newPermissionType)
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
