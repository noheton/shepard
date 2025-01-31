<script setup lang="ts">
import {
  BasicContainerAttributes,
  type BasicContainer,
} from "@dlr-shepard/backend-client";
import type { ContainerSortByAttribute } from "./containerSortByAttribute";
import {
  ContainerTypeName,
  type ContainerFilterType,
} from "./containerTypeFilter";

const router = useRouter();
const { queryParams } = useContainerListRouteParams();
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

defineProps<{
  itemsPerPage: number;
  serverItems: BasicContainer[];
  loading: boolean;
  pageCount: number;
}>();

const pageModel = ref<number>(queryParams.value.page);
const sortByAttributes = ref<ContainerSortByAttribute[]>(
  queryParams.value.sortBy ? [queryParams.value.sortBy] : [],
);

const sortBySearchParam = computed(() => {
  if (sortByAttributes.value[0])
    return JSON.stringify(sortByAttributes.value[0]);
  return undefined;
});

//watch page changes induced from search and filter components
watch(
  () => queryParams.value.page,
  () => {
    pageModel.value = queryParams.value.page;
  },
);

function onSortBy() {
  router.push({
    path: containersPath,
    query: {
      ...router.currentRoute.value.query,
      sortBy: sortBySearchParam.value,
    },
  });
}

function onPageChange() {
  router.push({
    path: containersPath,
    query: { ...router.currentRoute.value.query, page: pageModel.value },
  });
}
</script>

<template>
  <CommonDataTable
    v-model:sort-by="sortByAttributes"
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
    @update:sort-by="onSortBy"
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
        v-model="pageModel"
        :length="pageCount"
        :total-visible="6"
        @update:model-value="onPageChange"
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
