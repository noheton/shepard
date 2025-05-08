<script setup lang="ts">
import type { UserGroup } from "@dlr-shepard/backend-client";

defineProps<{
  userGroups: UserGroup[];
  loading: boolean;
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
    <template #[`item.id`]="{ item }: { item: UserGroup }">
      <span class="text-textbody">#{{ item.id }}</span>
    </template>
    <template #[`item.name`]="{ item }: { item: UserGroup }">
      <span class="text-textbody">{{ item.name }}</span>
    </template>
    <template #[`item.createdAt`]="{ item }: { item: UserGroup }">
      <div class="d-flex flex-column">
        <span class="text-textbody">
          {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
        </span>
        <span v-if="item.createdBy" class="text-textbody2">
          by {{ item.createdBy }}
        </span>
      </div>
    </template>
  </DataTable>
</template>
