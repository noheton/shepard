<script setup lang="ts">
import { PermissionType } from "@dlr-shepard/backend-client";

interface PermissionTypeInputProps {
  permissionType: PermissionType;
  updatePermissionType: (newValue: PermissionType) => void;
}

defineProps<PermissionTypeInputProps>();
</script>

<template>
  <v-row>
    <v-col class="pt-2">
      <v-select
        :model-value="permissionType"
        :items="Object.values(PermissionType)"
        label="Permissions*"
        variant="outlined"
        density="compact"
        color="primary"
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
    </v-col>
  </v-row>
</template>
