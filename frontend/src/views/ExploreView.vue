<script setup lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import { useSearchCollections } from "@/components/search/InlineSearchCollections";
import CollectionService from "@/services/collectionService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import type {
  Collection,
  DataObjectAttributes,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { refDebounced, useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue2-helpers/vue-router";

const router = useRouter();

const collections = ref<Collection[]>();

function retrieveCollections(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof DataObjectAttributes as DataObjectAttributes;
  CollectionService.getAllCollections({
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

const filterOptions = useStorage<FilterOptions>("explore-filter-options", {
  perPage: 10,
  orderBy: "createdAt",
  descending: false,
});
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
  retrieveCollections();
}

const userInput = ref("");
const userInputDebounced = refDebounced(userInput, 700);

const { results, totalResults, searchQuery } =
  useSearchCollections(userInputDebounced);

const searchRoute = computed(() => {
  const route = router.resolve("Search").route;
  route.query.queryType = "Collection";
  route.query.searchQuery = searchQuery.value;
  return route;
});

onMounted(() => {
  retrieveCollections();
  useTitle("Collections | shepard");
});
</script>

<template>
  <div class="view">
    <b-button-group class="float-right">
      <b-button
        v-b-modal.create-collection-modal
        v-b-tooltip.hover
        title="Create Collection"
        variant="primary"
      >
        <CreateIcon />
      </b-button>
    </b-button-group>

    <h4 class="title">Explore Collections</h4>

    <b-form-input
      id="userFormInput"
      v-model="userInput"
      class="mb-3"
      placeholder="Name, Username, ID or Description"
    ></b-form-input>

    <b-popover
      custom-class="wide-popover"
      target="userFormInput"
      triggers="focus"
      placement="bottom"
    >
      <template #title>
        Result Set ({{ totalResults }} total)
        <b-link class="float-right font-weight-normal" :to="searchRoute">
          Advanced Search
        </b-link>
      </template>
      <GenericEntityList :entities="results" />
    </b-popover>

    <FilterListLine
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="filterChanged($event)"
    />
    <GenericEntityList :entities="collections" />

    <b-pagination
      v-model="currentPage"
      :total-rows="totalRows"
      :per-page="filterOptions.perPage"
      align="center"
      size="sm"
      @change="retrieveCollections($event)"
    ></b-pagination>

    <CollectionModal
      modal-id="create-collection-modal"
      modal-name="Create Collection"
    />
  </div>
</template>
