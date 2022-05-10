<template>
  <div>
    <FilterListLine
      :max-objects="totalRows"
      :default-size="sizeSelected"
      :default-descending="descendingSelected"
      :default-order-by="orderBySelected"
      @filter-changed="filterChanged($event)"
    />
    <b-list-group>
      <DataObjectListItem
        v-for="(dataObject, index) in dataObjects"
        :key="index"
        :data-object="dataObject"
      />
    </b-list-group>
  </div>
</template>

<script lang="ts">
import DataObjectListItem from "@/components/dataobjects/DataObjectListItem.vue";
import FilterListLine, {
  FilterChangedData,
} from "@/components/generic/FilterListLine.vue";
import DataObjectService from "@/services/dataObjectService";
import {
  DataObject,
  GetAllDataObjectsOrderByEnum,
} from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface DataObjectListData {
  dataObjects: DataObject[];
  sizeSelected: number;
  orderBySelected: string;
  descendingSelected: boolean;
  currentPage: number;
}

export default Vue.extend({
  components: { DataObjectListItem, FilterListLine },
  props: {
    currentCollectionId: {
      type: Number,
      required: true,
    },
    parentId: {
      type: Number,
      default: undefined,
    },
    predecessorId: {
      type: Number,
      default: undefined,
    },
    successorId: {
      type: Number,
      default: undefined,
    },
    maxObjects: {
      type: Number,
      default: undefined,
    },
  },
  data() {
    return {
      dataObjects: [],
      currentPage: 1,
      descendingSelected: false,
      orderBySelected: GetAllDataObjectsOrderByEnum.CreatedAt,
      sizeSelected: 10,
    } as DataObjectListData;
  },
  computed: {
    totalRows(): number {
      if (this.maxObjects) {
        return this.maxObjects;
      } else if (this.dataObjects.length < this.sizeSelected) {
        return this.currentPage * this.sizeSelected;
      }
      return (this.currentPage + 1) * this.sizeSelected;
    },
  },
  mounted() {
    this.retrieveDataObjects();
  },
  methods: {
    filterChanged(options: FilterChangedData) {
      this.currentPage = options.currentPage;
      this.sizeSelected = options.currentSize;
      this.descendingSelected = options.descending;
      this.orderBySelected = options.orderBy;
      this.retrieveDataObjects();
    },
    retrieveDataObjects(page?: number) {
      const nextPage = page || this.currentPage;
      const nextOrderBy = this
        .orderBySelected as keyof typeof GetAllDataObjectsOrderByEnum as GetAllDataObjectsOrderByEnum;
      DataObjectService?.getAllDataObjects({
        collectionId: this.currentCollectionId,
        parentId: this.parentId,
        predecessorId: this.predecessorId,
        successorId: this.successorId,
        size: this.sizeSelected,
        orderBy: nextOrderBy,
        orderDesc: this.descendingSelected,
        page: nextPage - 1,
      })
        .then(response => {
          this.dataObjects = response;
        })
        .catch(e => {
          const error = "Error while fetching data objects: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
