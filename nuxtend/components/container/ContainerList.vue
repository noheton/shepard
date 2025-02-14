<script setup lang="ts">
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import {
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";
import { useContainerListQueryParams } from "./useContainerListQueryParams";

defineProps<{
  itemsPerPage: number;
  serverItems: BasicContainer[];
  loading: boolean;
  pageCount: number;
}>();

const router = useRouter();
const { queryParams } = useContainerListQueryParams();
const headers = [
  {
    title: "ID",
    key: "id",
    width: "10%",
    cellProps: {
      class: "text-body-1",
    },
  },
  {
    title: "Name",
    key: BasicContainerAttributes.Name,
    width: "30%",
    cellProps: {
      class: "text-subtitle-2 word-wrap-anywhere",
    },
  },
  {
    title: "Container Type",
    key: BasicContainerAttributes.Type,
    width: "20%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "Created by",
    key: BasicContainerAttributes.CreatedBy,
    width: "20%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    title: "Created at",
    key: BasicContainerAttributes.CreatedAt,
    width: "20%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
];

function onSortBy(args: { key: string; order: "asc" | "desc" }[]) {
  if (args[0]) {
    router.push({
      path: containersPath,
      query: {
        ...router.currentRoute.value.query,
        page: 1,
        sortBy: JSON.stringify(args[0]),
      },
    });
    return;
  }
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      page: 1,
      sortBy: undefined,
    },
  });
}

function onPageChange(page: number) {
  router.push({
    path: containersPath,
    query: { ...router.currentRoute.value.query, page },
  });
}
</script>

<template>
  <DataTable
    :sort-by="queryParams.sortBy ? [queryParams.sortBy] : []"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :cell-props="{
      class: 'text-textbody1',
    }"
    :headers="headers"
    :items="serverItems"
    :items-per-page="itemsPerPage"
    :loading="loading"
    @update:sort-by="onSortBy"
  >
    <template #[`item.id`]="{ value }: { value: ContainerFilterType }">
      #{{ value }}
    </template>
    <template #[`item.type`]="{ value }: { value: ContainerFilterType }">
      {{ ContainerTypeName[value] }}
    </template>

    <template #[`item.createdAt`]="{ value }: { value: Date }">
      {{ toShortDateString(value) }}
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        :model-value="queryParams.page ?? 0"
        :length="pageCount"
        :total-visible="6"
        @update:model-value="onPageChange"
      />
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
