<script setup lang="ts">
import {
  CollectionApi,
  type Collection,
  type DataObjectAttributes,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { refDebounced, useStorage } from "@vueuse/core";
import type { FilterChangedData, FilterOptions } from "~/utils/helpers";
import { useSearchCollections } from "~/utils/InlineSearchCollections";
import CollectionList from "./CollectionList.vue";

const router = useRouter();

const filterInput = ref("");
const collections = ref<Collection[]>([]);
const page = ref(1);

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
      collections.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collections");
    });
}

const currentPage = ref(1);
const totalRows = computed(() => {
  if (collections.value)
    return getTotalRows(
      collections.value.length,
      filterOptions.value.perPage,
      currentPage.value,
    );
  else return 0;
});
function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  filterOptions.value.perPage = options.perPage;
  filterOptions.value.descending = options.descending;
  filterOptions.value.orderBy = options.orderBy;
  fetchCollections();
}

const filterInputDebounced = refDebounced(filterInput, 700);

const { results, totalResults } = useSearchCollections(filterInputDebounced);

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
      <!-- <v-btn icon="mdi-plus" color="primary" /> -->
    </div>
    <v-menu :close-on-content-click="false">
      <template #activator="{ props }">
        <v-text-field
          id="userFormInput"
          v-model="filterInput"
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
            <v-card-item>
              <v-card-title>
                <h4>{{ result.name }}</h4>
                ID: {{ result.id }}
              </v-card-title>
              <v-card-subtitle>
                {{
                  "created at " + result.createdAt + " by " + result.createdBy
                }}
              </v-card-subtitle>
            </v-card-item>
          </v-card>
        </template>
      </v-card>
    </v-menu>

    <CollectionSortingOptions
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="filterChanged($event)"
    />
    <div :style="{ marginTop: '10px', width: '100%' }">
      <CollectionList
        :max-objects="totalRows"
        :collections="collections"
        :page="page"
      />
    </div>
  </div>
</template>
