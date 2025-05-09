<script setup lang="ts">
import type { UserGroup } from "@dlr-shepard/backend-client";

defineProps<{
  userGroups: UserGroup[];
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "select-user-group", userGroupId: number): void;
}>();

const headers = [
  { title: "ID", key: "id", sortable: true, width: "20%" },
  { title: "Name", key: "name", sortable: true, width: "40%" },
  { title: "Created at", key: "createdAt", sortable: true },
  {
    title: "",
    value: "actions",
  },
];
</script>

<template>
  <DataTable
    class="pt-4"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
      style: 'background-color: rgb(var(--v-theme-divider2))',
    }"
    :headers="headers"
    :items="userGroups"
    :loading="loading"
    :items-per-page="-1"
    :hide-default-footer="true"
    hover
  >
    <template #item="rowProps">
      <v-data-table-row
        v-bind="rowProps"
        @click="emit('select-user-group', rowProps.item.id)"
      >
        <template #[`item.id`]>
          <span class="text-textbody">#{{ rowProps.item.id }}</span>
        </template>
        <template #[`item.name`]>
          <span class="text-textbody">{{ rowProps.item.name }}</span>
        </template>
        <template #[`item.createdAt`]>
          <div class="d-flex flex-column">
            <span class="text-textbody">
              {{
                rowProps.item.createdAt
                  ? toShortDateString(rowProps.item.createdAt)
                  : "-"
              }}
            </span>
            <span v-if="rowProps.item.createdBy" class="text-textbody2">
              by {{ rowProps.item.createdBy }}
            </span>
          </div>
        </template>
      </v-data-table-row>
    </template>
  </DataTable>
</template>
