<script setup lang="ts">
import {
  BasicContainerAttributes,
  ContainerType,
  SearchApi,
  type BasicContainer,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import ContainerTypeSelect from "./ContainerTypeSelect.vue";
import { buildQueryString } from "./buildQueryString";
import type { ContainerFilterType } from "./containerTypeFilter";

const itemsPerPage = 20;

const serverItems = ref<BasicContainer[]>([]);
const pageCount = ref<number>(0);
const loading = ref<boolean>(true);
const selectedFilter = ref<ContainerFilterType | null | undefined>(undefined);
const searchText = ref<string | null>(null);
const dataTableOptions = ref<{
  page: number;
  sortByAttributes: { key: BasicContainerAttributes; order: string }[];
}>({
  page: 1,
  sortByAttributes: [
    { key: BasicContainerAttributes.CreatedAt, order: "desc" },
  ],
});

const searchQuery = computed(() => buildQueryString(searchText.value));

watch(
  dataTableOptions,
  () => {
    searchContainers();
  },
  { deep: true },
);

function resetDataTableOptions() {
  if (dataTableOptions.value.page === 1) searchContainers();
  else dataTableOptions.value.page = 1;
}

function onSelectUpdate() {
  resetDataTableOptions();
}

function onSearch() {
  resetDataTableOptions();
}

function searchContainers() {
  loading.value = true;
  const orderByParams = dataTableOptions.value.sortByAttributes[0]
    ? {
        orderBy: dataTableOptions.value.sortByAttributes[0].key,
        orderDesc: dataTableOptions.value.sortByAttributes[0].order === "desc",
      }
    : null;
  createApiInstance(SearchApi)
    .searchContainers({
      containerSearchBody: {
        searchParams: {
          query: searchQuery.value,
          queryType: selectedFilter.value ?? ContainerType.Basic,
        },
      },
      page: dataTableOptions.value.page - 1,
      size: itemsPerPage,
      ...orderByParams,
    })
    .then(response => {
      if (
        response.results !== undefined &&
        response.totalResults !== undefined
      ) {
        serverItems.value = response.results;
        pageCount.value = Math.ceil(response.totalResults / itemsPerPage);
      }
      loading.value = false;
    })
    .catch(e => {
      handleError(e as ResponseError, "searching containers");
    });
}

searchContainers();
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row no-gutters>
        <v-col cols="12" no-gutters>
          <CommonTooltip>
            <h1 class="text-h1 pr-4">Containers</h1>
            <template #content>
              <div>
                The data you reference in your project collections is stored in
                containers.
              </div>
              <div>
                There are different types of containers for the different types
                of data they store.
              </div>
            </template>
          </CommonTooltip>
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col cols="6" class="pt-14">
          <ContainerSearchField v-model="searchText" @on-search="onSearch" />
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col cols="4" no-gutters class="pt-8">
          <ContainerTypeSelect
            v-model="selectedFilter"
            @on-select-update="onSelectUpdate"
          />
        </v-col>
      </v-row>
      <v-row no-gutters>
        <v-col cols="12" no-gutters class="pt-4">
          <ContainerList
            v-model="dataTableOptions"
            :items-per-page="itemsPerPage"
            :server-items="serverItems"
            :loading="loading"
            :page-count="pageCount"
          />
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<style lang="scss" scoped>
:deep(.v-field--variant-outlined.v-field--focused .v-field__outline) {
  --v-field-border-width: 1px;
}
</style>
