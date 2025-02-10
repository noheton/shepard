<script setup lang="ts">
import DataObjectDataMetaCell from "./DataObjectDataMetaCell.vue";
import type { DataReference } from "./dataReference";
import type { DataTableElement } from "./dataTableElement";
import { mapDataReferenceToDataTableElement } from "./dataTableElementMappingUtil";

interface DataObjectDataReferencesTable {
  dataReferences: Array<DataReference>;
}
const props = defineProps<DataObjectDataReferencesTable>();

const tableItems: Array<DataTableElement> = props.dataReferences.map(
  mapDataReferenceToDataTableElement,
);

const headers = [
  {
    title: "Type",
    value: "type",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Name",
    value: "name",
    sort: (a: string, b: string) => a.localeCompare(b),
  },
  {
    title: "Meta",
    value: "meta",
    sort: (a: DataTableElement["meta"], b: DataTableElement["meta"]) =>
      a.id - b.id,
  },
  {
    title: "Created",
    value: "created",
    sort: (a: DataTableElement["created"], b: DataTableElement["created"]) =>
      a.createdAt.valueOf() - b.createdAt.valueOf(),
  },
];

const page = ref<number>(1);
const itemsPerPage = 10;

const pageCount = Math.ceil(tableItems.length / itemsPerPage);
</script>

<template>
  <EmptyListIcon v-if="tableItems.length === 0" label="No data yet" />
  <DataTable
    v-else
    v-model:page="page"
    :headers="headers"
    :items="tableItems"
    :items-per-page="itemsPerPage"
  >
    <template #[`item.meta`]="{ value }: { value: DataTableElement['meta'] }">
      <DataObjectDataMetaCell :meta="value" />
    </template>
    <template
      #[`item.created`]="{ value }: { value: DataTableElement['created'] }"
    >
      <CreatedTableCell
        :created-at="value.createdAt"
        :created-by="value.createdBy"
      />
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination v-model="page" :length="pageCount" />
    </template>
  </DataTable>
</template>

<style scoped lang="scss">
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }
}
</style>
