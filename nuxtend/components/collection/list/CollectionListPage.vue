<script setup lang="ts">
import {
  CollectionApi,
  type BasicEntity,
  type DataObjectAttributes,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useStorage } from "@vueuse/core";
import type { FilterChangedData, FilterOptions } from "~/utils/helpers";

const entities = ref<BasicEntity[]>([]);
const totalPages = ref(1);

const filterOptions = useStorage<FilterOptions>(
  "collection-list-filter-options",
  {
    perPage: 10,
    orderBy: "createdAt",
    descending: false,
  },
);

function fetchCollections(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof DataObjectAttributes as DataObjectAttributes;
  createApiInstance(CollectionApi)
    .getAllCollections({
      size: filterOptions.value.perPage,
      page: nextPage - 1,
      orderBy: nextOrderBy,
      orderDesc: filterOptions.value.descending,
    })
    .then(response => {
      entities.value = response;
      updateTotalPages();
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collections");
    });
}

const currentPage = ref(1);

function updateTotalPages() {
  if (entities.value.length >= filterOptions.value.perPage)
    totalPages.value = currentPage.value + 1;
  else if (totalPages.value > 1 && currentPage.value == 1) --totalPages.value;
}

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  filterOptions.value.perPage = options.perPage;
  filterOptions.value.descending = options.descending;
  filterOptions.value.orderBy = options.orderBy;
  fetchCollections();
}

onMounted(() => {
  fetchCollections();
});
</script>

<template>
  <div style="max-width: 1200px">
    <v-container fluid class="pa-0 fill-height" max-width="1200px">
      <v-row no-gutters>
        <v-col cols="12" no-gutters><CollectionListDataTable /></v-col>
        <v-col cols="12" class="pa-0 pt-16 pb-14" no-gutters>
          <h1 class="text-h1">Explore Collections</h1>
        </v-col>
        <v-col cols="12" no-gutters><CollectionListActionsBar /></v-col>
        <v-col cols="12" no-gutters>
          <CollectionListSortingOptions
            :max-objects="totalPages"
            :current-page="currentPage"
            :filter-options="filterOptions"
            @filter-changed="options => filterChanged(options)"
          />
        </v-col>
        <v-col cols="12" no-gutters>
          <CommonDataTable
            :items="entities"
            :headers="[
              { title: 'ID', value: 'id' },
              { title: 'Name', value: 'name' },
              {
                title: 'Last changed',
                key: 'lastChanged',
                value: (item: BasicEntity) => {
                  if (item.updatedAt) return toShortDateString(item.updatedAt);
                  return toShortDateString(item.createdAt);
                },
              },
              { title: 'Created by', value: 'createdBy' },
            ]"
            items-per-page="-1"
          >
            <template #bottom>
              <v-divider :thickness="8" color="divider2" opacity="1" />
              <v-pagination
                v-model="currentPage"
                :length="totalPages"
                @update:model-value="fetchCollections(currentPage)"
              />
            </template>
          </CommonDataTable>
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>
