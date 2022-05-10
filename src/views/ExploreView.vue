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
import CollectionService from "@/services/collectionService";
import { emitter } from "@/utils/event-bus";
import { totalRows } from "@/utils/helpers";
import {
  Collection,
  GetAllCollectionsOrderByEnum,
} from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface ExploreData {
  collections?: Collection[];
  perPage: number;
  currentPage: number;
  orderBy: string;
  descending: boolean;
  deletedAlert: boolean;
}

export default Vue.extend({
  components: { GenericEntityList, FilterListLine, CollectionModal },
  data() {
    return {
      collections: undefined,
      perPage: 10,
      currentPage: 1,
      orderBy: "createdAt",
      descending: false,
      deletedAlert: false,
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
  },
});
</script>
