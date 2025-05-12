<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";

defineProps<{
  limitedPermissionSet?: PermissionType[];
  noRequiredHint?: boolean;
}>();

const permissionType = defineModel<PermissionType>("permissionType", {
  required: true,
});
</script>

<template>
  <v-select
    v-model:model-value="permissionType"
    :items="limitedPermissionSet ?? Object.values(PermissionType)"
    :label="`Permissions${noRequiredHint ? '' : '*'}`"
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
