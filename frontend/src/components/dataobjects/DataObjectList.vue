<script setup lang="ts">
import DataObjectListItem from "@/components/dataobjects/DataObjectListItem.vue";
import FilterListLine from "@/components/generic/FilterListLine.vue";
import Loading from "@/components/generic/Loading.vue";
import DataObjectService from "@/services/dataObjectService";
import { handleError } from "@/utils/error-handling";
import {
  getTotalRows,
  type FilterChangedData,
  type FilterOptions,
} from "@/utils/helpers";
import type {
  DataObject,
  GetAllDataObjectsOrderByEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { useStorage } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";

const props = defineProps({
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
});

const dataObjects = ref<DataObject[]>();
const currentPage = ref(1);
const filterOptions = useStorage<FilterOptions>("do-filter-options", {
  perPage: 25,
  orderBy: "createdAt",
  descending: false,
});
const totalRows = computed(() => {
  if (props.maxObjects) {
    return props.maxObjects;
  } else if (dataObjects.value) {
    return getTotalRows(
      dataObjects.value.length,
      filterOptions.value.perPage,
      currentPage.value,
    );
  } else {
    return 0;
  }
});
function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  filterOptions.value.perPage = options.perPage;
  filterOptions.value.descending = options.descending;
  filterOptions.value.orderBy = options.orderBy;
  retrieveDataObjects();
}

function retrieveDataObjects(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy = filterOptions.value
    .orderBy as keyof typeof GetAllDataObjectsOrderByEnum as GetAllDataObjectsOrderByEnum;
  DataObjectService?.getAllDataObjects({
    collectionId: props.currentCollectionId,
    parentId: props.parentId,
    predecessorId: props.predecessorId,
    successorId: props.successorId,
    size: filterOptions.value.perPage,
    orderBy: nextOrderBy,
    orderDesc: filterOptions.value.descending,
    page: nextPage - 1,
  })
    .then(response => {
      dataObjects.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching data objects");
    });
}

onMounted(() => {
  retrieveDataObjects();
});
</script>

<template>
  <div>
    <FilterListLine
      :max-objects="totalRows"
      :current-page="currentPage"
      :filter-options="filterOptions"
      @filter-changed="filterChanged($event)"
    />
    <div v-if="dataObjects == undefined"><Loading /></div>
    <b-list-group v-else>
      <DataObjectListItem
        v-for="(dataObject, index) in dataObjects"
        :key="index"
        :data-object="dataObject"
      />
    </b-list-group>
  </div>
</template>
