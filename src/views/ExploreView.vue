<script setup lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import FilterListLine from "@/components/generic/FilterListLine.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import CollectionService from "@/services/collectionService";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import {
  GetAllCollectionsOrderByEnum,
  ResponseError,
  SearchParamsQueryTypeEnum,
  type Collection,
} from "@dlr-shepard/shepard-client";
import { refDebounced, useStorage, useTitle } from "@vueuse/core";
import { computed, onMounted, ref, watch } from "vue";

const collections = ref<Collection[]>();

function retrieveCollections(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof GetAllCollectionsOrderByEnum as GetAllCollectionsOrderByEnum;
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
const collectionsResultSet = ref<Collection[]>([]);
const totalResults = ref(0);

watch(userInputDebounced, () => {
  if (
    userInputDebounced.value.length != 0 &&
    (userInputDebounced.value.length >= 3 ||
      !isNaN(Number(userInputDebounced.value)))
  ) {
    inlineSearch();
  } else {
    collectionsResultSet.value = [];
  }
});

function inlineSearch() {
  const searchQuery = {
    OR: [
      {
        property: "name",
        value: userInput.value,
        operator: "contains",
      },
      {
        property: "createdBy",
        value: userInput.value,
        operator: "contains",
      },
      {
        property: "description",
        value: userInput.value,
        operator: "contains",
      },
      {
        property: "id",
        value: Number(userInput.value),
        operator: "eq",
      },
    ],
  };
  SearchService.search({
    searchBody: {
      scopes: [
        {
          traversalRules: [],
        },
      ],
      searchParams: {
        query: JSON.stringify(searchQuery),
        queryType: SearchParamsQueryTypeEnum.Collection,
      },
    },
  })
    .then(response => {
      collectionsResultSet.value = [];
      totalResults.value = response.resultSet?.length || 0;
      response.resultSet?.slice(0, 10).forEach(result => {
        if (result.collectionId) {
          retrieveCollectionById(result.collectionId);
        }
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching search data");
    });
}

function retrieveCollectionById(collectionId: number) {
  CollectionService.getCollection({
    collectionId: collectionId,
  })
    .then(response => {
      collectionsResultSet.value = [...collectionsResultSet.value, response];
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

onMounted(() => {
  retrieveCollections();
  useTitle("Collections | shepard");
});
</script>

<template>
  <div class="explore">
    <div class="component">
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

      <h4 class="mb-4">Explore Collections</h4>

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
        <template #title>Result Set ({{ totalResults }} total)</template>
        <GenericEntityList :entities="collectionsResultSet" />
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
  </div>
</template>
