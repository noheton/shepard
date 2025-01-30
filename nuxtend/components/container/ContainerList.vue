<script setup lang="ts">
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import {
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";

const dataTableOptions = defineModel<{
  page: number;
  sortByAttributes: { key: BasicContainerAttributes; order: string }[];
}>({ required: true });

defineProps<{
  itemsPerPage: number;
  serverItems: BasicContainer[];
  loading: boolean;
  pageCount: number;
}>();

const headers = [
  {
    title: "ID",
    key: "id",
  },
  {
    title: "Name",
    key: BasicContainerAttributes.Name,
  },
  {
    title: "Container Type",
    key: BasicContainerAttributes.Type,
    cellProps: {
      class: "text-textbody2",
    },
  },
  {
    title: "Created by",
    key: BasicContainerAttributes.CreatedBy,
    cellProps: {
      class: "text-textbody2",
    },
  },
  {
    title: "Created at",
    key: BasicContainerAttributes.CreatedAt,
    cellProps: {
      class: "text-textbody2",
    },
  },
];
</script>

<template>
  <CommonDataTable
    v-model:sort-by="dataTableOptions.sortByAttributes"
    :header-props="{
      class: 'text-subtitle-2',
    }"
    :cell-props="{
      class: 'text-body-1',
    }"
    :headers="headers"
    :items="serverItems"
    :items-per-page="itemsPerPage"
    :loading="loading"
  >
    <template #[`item.type`]="{ value }: { value: ContainerFilterType }">
      {{ ContainerTypeName[value] }}
    </template>

    <template #[`item.createdAt`]="{ value }: { value: Date }">
      {{ toShortDateString(value) }}
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        v-model="dataTableOptions.page"
        :length="pageCount"
        :total-visible="6"
      />
    </template>
  </CommonDataTable>
</template>

<style scoped lang="scss">
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }
}
</style>
