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
          @keyup.enter="inlineSearch()"
        ></b-form-input>
        <b-input-group-append>
          <b-button variant="dark" @click="inlineSearch()"> Search </b-button>
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

      <b-alert
        :show="deletedAlert"
        dismissible
        variant="dark"
        @dismissed="deletedAlert = false"
      >
        Successfully deleted
      </b-alert>

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

<script lang="ts">
import CollectionModal from "@/components/dataobjects/CollectionModal.vue";
import FilterListLine, {
  FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import GenericEntityList from "@/components/generic/GenericEntityList.vue";
import Loading from "@/components/generic/Loading.vue";
import CollectionService from "@/services/collectionService";
import SearchService from "@/services/searchService";
import { emitter } from "@/utils/event-bus";
import { totalRows } from "@/utils/helpers";
import {
  Collection,
  GetAllCollectionsOrderByEnum,
  SearchParamsQueryTypeEnum,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface ExploreData {
  collections?: Collection[];
  collectionsResultSet: Collection[];
  collectionsFound?: number;
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
  deletedAlert: boolean;
  userInput: string;
}

export default defineComponent({
  components: { GenericEntityList, FilterListLine, CollectionModal, Loading },
  data() {
    return {
      collections: undefined,
      collectionsResultSet: [],
      collectionsFound: undefined,
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
      deletedAlert: false,
      userInput: "",
    } as ExploreData;
  },
  computed: {
    totalRows(): number {
      if (this.collections)
        return totalRows(
          this.collections.length,
          this.perPage,
          this.currentPage,
        );
      else return 0;
    },
  },
  mounted() {
    this.retrieveCollections();
  },
  methods: {
    filterChanged(options: FilterChangedData) {
      this.currentPage = options.currentPage;
      this.perPage = options.currentSize;
      this.descending = options.descending;
      this.orderBy = options.orderBy;
      this.retrieveCollections();
    },
    retrieveCollections(page?: number) {
      const nextPage = page || this.currentPage;
      const nextOrderBy = this
        .orderBy as keyof typeof GetAllCollectionsOrderByEnum as GetAllCollectionsOrderByEnum;
      CollectionService.getAllCollections({
        size: this.perPage,
        page: nextPage - 1,
        orderBy: nextOrderBy,
        orderDesc: this.descending,
      })
        .then(response => {
          this.collections = response;
        })
        .catch(e => {
          const error = "Error while fetching collections: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handlePrepare() {
      this.collectionsResultSet = [];
      this.collectionsFound = undefined;
    },

    retrieveCollectionById(collectionId: number) {
      CollectionService.getCollection({
        collectionId: collectionId,
      })
        .then(response => {
          this.collectionsResultSet.push(response);
        })
        .catch(e => {
          const error = "Error while fetching collection: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    inlineSearch() {
      this.$bvModal.show("searchDataModal");
      const searchQuery = {
        OR: [
          {
            property: "name",
            value: this.userInput,
            operator: "contains",
          },
          {
            property: "createdBy",
            value: this.userInput,
            operator: "contains",
          },
          {
            property: "description",
            value: this.userInput,
            operator: "contains",
          },
          {
            property: "id",
            value: Number(this.userInput),
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
          this.collectionsFound = response.resultSet?.length || 0;
          response.resultSet?.forEach(result => {
            if (result.collectionId) {
              this.retrieveCollectionById(result.collectionId);
            }
          });
        })
        .catch(e => {
          const error = "Error while fetching search Data: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
  },
});
</script>
