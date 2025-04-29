<script setup lang="ts">
import type { DisplayItem } from "./CreateDataReferenceDialog.vue";

const props = defineProps<{
  items: DisplayItem[];
  loading: boolean;
}>();
const emit = defineEmits<{
  (e: "sendedOidList", oid: string[]): void;
}>();

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
  emit("sendedOidList", selected.value);
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
    item-value="oid"
    hover
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
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }

  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
