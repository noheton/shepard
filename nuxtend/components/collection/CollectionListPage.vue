<script setup lang="ts">
import {
  CollectionApi,
  type BasicEntity,
  type DataObjectAttributes,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { refDebounced, useStorage } from "@vueuse/core";
import type { FilterChangedData, FilterOptions } from "~/utils/helpers";
import { useSearchCollections } from "~/utils/InlineSearchCollections";
import CollectionList from "./CollectionList.vue";
import CollectionCreateDialog from "./create/CollectionCreateDialog.vue";

const router = useRouter();

const filterInput = ref("");
const entities = ref<BasicEntity[]>([]);
const page = ref(1);
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

const filterInputDebounced = refDebounced(filterInput, 700);

const { results, totalResults } = useSearchCollections(filterInputDebounced);

const showCreateDialog = ref(false);

onMounted(() => {
  fetchCollections();
});
</script>

<template>
  <div
    :style="{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      width: '60%',
    }"
  >
    <div
      :style="{
        display: 'flex',
        justifyContent: 'space-between',
        marginTop: '5px',
        width: '100%',
      }"
    >
      <h4 :style="{ padding: '12px' }">Explore Collections</h4>
      <v-btn color="primary" @click="showCreateDialog = true">
        <template #prepend><v-icon icon="mdi-plus-circle" /></template>
        Create new collection
      </v-btn>
      <CollectionCreateDialog
        v-model:show-dialog="showCreateDialog"
        @collection-created="id => router.push(collectionsPath + id)"
      />
    </div>
    <v-menu :close-on-content-click="false">
      <template #activator="{ props }">
        <v-text-field
          id="userFormInput"
          v-model="filterInput"
          color="primary"
          placeholder="Name, Username, ID or Description"
          :style="{ width: '100%', marginTop: '5px' }"
          v-bind="props"
        />
      </template>

      <v-card>
        <v-card-title>
          <h3>Result set ({{ totalResults }} total)</h3>
        </v-card-title>
        <template v-for="result in results" :key="result.id">
          <v-card @click="router.push('/collections/' + result.id)">
            <CollectionListItemContent :collection="result" />
          </v-card>
        </template>
      </v-card>
    </v-menu>

    <CollectionSortingOptions
      :max-objects="totalPages"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="options => filterChanged(options)"
    />
    <div :style="{ marginTop: '10px', width: '100%' }">
      <CollectionList
        :pagination-length="totalPages"
        :max-objects="filterOptions.perPage"
        :collections="entities"
        :page="page"
      />
      <v-pagination
        v-model="currentPage"
        :length="totalPages"
        @update:model-value="fetchCollections(currentPage)"
      />
    </div>
  </div>
</template>
