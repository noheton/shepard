<script setup lang="ts">
import type { fileItem } from "./CreateDataReferenceDialog.vue";
import type { FileRef } from "./DataRef";

const props = defineProps<{
  items: fileItem[] | undefined;
  loading: boolean;
}>();

const fileReference = defineModel<FileRef | undefined>("fileReference", {
  required: true,
});

const selected = ref<string[]>([]);
const headers = [
  {
    title: "Name",
    key: "filename",
    width: "60%",
  },
  {
    title: "Created at",
    key: "createdAt",
    width: "40%",
  },
];

function selectionChanged() {
  fileReference.value = { fileOids: selected.value };
}
</script>

<template>
  <DataTable
    v-model="selected"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items="props.items"
    :loading="loading"
    item-value="oid"
    hide-default-footer
    show-select
    @update:model-value="() => selectionChanged()"
  >
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
    </template>
  </DataTable>
</template>

<style scoped lang="scss">
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
