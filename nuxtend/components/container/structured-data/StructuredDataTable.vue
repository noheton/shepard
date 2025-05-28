<script setup lang="ts">
import type { StructuredData } from "@dlr-shepard/backend-client";

defineProps<{
  items: StructuredData[];
  isAllowedToEdit: boolean;
  loading: boolean;
}>();

const emit = defineEmits<{
  (
    e:
      | "delete-item"
      | "show-structured-data-content-dialog"
      | "download-structured-data",
    value: StructuredData,
  ): void;
}>();

const headers = [
  { title: "Name", key: "name", sortable: true },
  { title: "Oid", key: "oid", sortable: false },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", value: "actions", sortable: false },
];

const sortBy = ref([{ key: "name", order: "asc" }]);
const itemsPerPage = 10;
</script>

<template>
  <DataTable
    v-model:sort-by="sortBy"
    :items-per-page="itemsPerPage"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items-for-pagination="items"
    :loading="loading"
  >
    <template #[`item.oid`]="{ item }: { item: StructuredData }">
      <CopyTextButton :text="item.oid" />
    </template>
    <template #[`item.createdAt`]="{ item }: { item: StructuredData }">
      {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
    </template>
    <template #[`item.actions`]="{ item }: { item: StructuredData }">
      <ActionContainer>
        <ActionButton
          icon="mdi-eye-outline"
          @click="() => emit('show-structured-data-content-dialog', item)"
        />
        <ActionButton
          icon="mdi-tray-arrow-down"
          @click="() => emit('download-structured-data', item)"
        />
        <ActionButton
          v-if="isAllowedToEdit"
          icon="mdi-delete-outline"
          @click="() => emit('delete-item', item)"
        />
      </ActionContainer>
    </template>
  </DataTable>
</template>
