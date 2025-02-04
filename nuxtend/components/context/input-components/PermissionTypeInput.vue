<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";

const permissionType = defineModel<PermissionType>("permissionType", {
  required: true,
});
</script>

<template>
  <v-select
    v-model:model-value="permissionType"
    :items="Object.values(PermissionType)"
    label="Permissions*"
    variant="outlined"
    density="compact"
    color="primary"
    require
    hide-details
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
</template>
