<script setup lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import FilterListLine, {
  type FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import Loading from "@/components/generic/Loading.vue";
import CollectionService from "@/services/collectionService";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import { getTotalRows } from "@/utils/helpers";
import {
  GetAllCollectionsOrderByEnum,
  ResponseError,
  SearchParamsQueryTypeEnum,
  type Collection,
} from "@dlr-shepard/shepard-client";
import { computed, onMounted, ref } from "vue";

const collections = ref<Collection[]>();
function retrieveCollections(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy =
    orderBy.value as keyof typeof GetAllCollectionsOrderByEnum as GetAllCollectionsOrderByEnum;
  CollectionService.getAllCollections({
    size: perPage.value,
    page: nextPage - 1,
    orderBy: nextOrderBy,
    orderDesc: descending.value,
  })
    .then(response => {
      collections.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collections");
    });
}

const perPage = ref(10);
const currentPage = ref(1);
const orderBy = ref("createdAt");
const descending = ref(false);
const userInput = ref("");
const totalRows = computed(() => {
  if (collections.value)
    return getTotalRows(
      collections.value.length,
      perPage.value,
      currentPage.value,
    );
  else return 0;
});
function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  perPage.value = options.currentSize;
  descending.value = options.descending;
  orderBy.value = options.orderBy;
  retrieveCollections();
}

const collectionsResultSet = ref<Collection[]>([]);
const collectionsFound = ref<number>();
function handlePrepare() {
  collectionsResultSet.value = [];
  collectionsFound.value = undefined;
}
function retrieveCollectionById(collectionId: number) {
  CollectionService.getCollection({
    collectionId: collectionId,
  })
    .then(response => {
      collectionsResultSet.value.push(response);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}
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
      collectionsFound.value = response.resultSet?.length || 0;
      response.resultSet?.forEach(result => {
        if (result.collectionId) {
          retrieveCollectionById(result.collectionId);
        }
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching search data");
    });
}

onMounted(() => {
  retrieveCollections();
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

      <b-input-group class="mb-3">
        <b-form-input
          v-model="userInput"
          placeholder="Name, Username, ID or Description"
        ></b-form-input>
        <b-input-group-append>
          <b-button
            v-b-modal.searchDataModal
            variant="secondary"
            @click="inlineSearch()"
          >
            Search
          </b-button>
        </b-input-group-append>
      </b-input-group>

      <b-modal
        id="searchDataModal"
        title="Result Set"
        size="lg"
        ok-only
        @show="handlePrepare()"
      >
        <GenericEntityList
          v-if="collectionsFound == collectionsResultSet.length"
          :entities="collectionsResultSet"
        />
        <Loading v-else />
      </b-modal>

      <FilterListLine
        :max-objects="totalRows"
        :default-page="currentPage"
        :default-size="perPage"
        :default-descending="descending"
        :default-order-by="orderBy"
        @filter-changed="filterChanged($event)"
      />
      <GenericEntityList :entities="collections" />

      <b-pagination
        v-model="currentPage"
        :total-rows="totalRows"
        :per-page="perPage"
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
