<script lang="ts" setup>
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";
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
    :cell-props="{
      class: 'text-textbody1',
    }"
    :header-props="{
      class: 'text-subtitle-2 text-textbody1',
    }"
    :headers="headers"
    :items="serverItems"
    :items-per-page="itemsPerPage"
    :loading="loading"
    :sort-by="queryParams.sortBy ? [queryParams.sortBy] : []"
    @update:sort-by="onSortBy"
  >
    <template #item="rowProps">
      <v-data-table-row
        v-bind="rowProps"
        @click="
          router.push(
            containersPath +
              containerTypeUrlPathSegmentMappings[
                rowProps.item.type as ContainerFilterType
              ] +
              rowProps.item.id,
          )
        "
      >
        <template #[`item.id`]>
          {{ rowProps.item.id }}
        </template>
        <template #[`item.name`]>
          {{ rowProps.item.name }}
        </template>
        <template #[`item.type`]>
          {{ ContainerTypeName[rowProps.item.type as ContainerFilterType] }}
        </template>
        <template #[`item.createdBy`]>{{ rowProps.item.createdBy }}</template>
        <template #[`item.createdAt`]>
          {{ toShortDateString(rowProps.item.createdAt) }}
        </template>
      </v-data-table-row>
    </template>
    <template #bottom>
      <v-divider :thickness="8" color="divider2" opacity="1" />
      <v-pagination
        :length="pageCount"
        :model-value="queryParams.page ?? 1"
        :total-visible="6"
        @update:model-value="onPageChange"
      />
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }

  :deep(tbody > tr) {
    cursor: pointer;
  }
}
</style>
