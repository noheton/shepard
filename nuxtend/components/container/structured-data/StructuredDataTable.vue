<script setup lang="ts">
import type { StructuredData } from "@dlr-shepard/backend-client";
import { useClipboard } from "@vueuse/core";

defineProps<{
  items: StructuredData[];
  isAllowedToEdit: boolean;
  loading: boolean;
}>();

const emit = defineEmits<{
  (e: "delete-item", value: StructuredData): void;
}>();

const headers = ref([
  { title: "Name", key: "name", sortable: true },
  { title: "Oid", key: "oid", sortable: false },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", value: "actions", sortable: false },
]);

const sortBy = ref([{ key: "name", order: "asc" }]);

const { copy } = useClipboard();
const copyOid = (oid: string | undefined) => {
  if (oid) {
    copy(oid);
    emitSuccess(`Copied "${oid}"`);
  }
};
</script>

<template>
  <DataTable
    v-model:sort-by="sortBy"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items="items"
    :loading="loading"
    hover
  >
    <template #[`item.oid`]="{ item }: { item: StructuredData }">
      {{ item.oid }}
      <ActionButton
        icon="mdi-content-copy"
        color="medium-emphasis"
        @click="() => copyOid(item.oid)"
      />
    </template>
    <template #[`item.createdAt`]="{ item }: { item: StructuredData }">
      {{ item.createdAt ? toShortDateString(item.createdAt) : "-" }}
    </template>
    <template #[`item.actions`]="{ item }: { item: StructuredData }">
      <ActionContainer>
        <ActionButton
          v-if="isAllowedToEdit"
          icon="mdi-delete-outline"
          @click="() => emit('delete-item', item)"
        />
      </ActionContainer>
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination :total-visible="6" />
    </template>
  </DataTable>
</template>
