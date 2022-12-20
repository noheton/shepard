<script setup lang="ts">
import DataObjectListItem from "@/components/dataobjects/DataObjectListItem.vue";
import FilterListLine from "@/components/generic/FilterListLine.vue";
import Loading from "@/components/generic/Loading.vue";
import DataObjectService from "@/services/dataObjectService";
import { handleError } from "@/utils/error-handling";
import type { FilterChangedData } from "@/utils/helpers";
import {
  GetAllDataObjectsOrderByEnum,
  ResponseError,
  type DataObject,
} from "@dlr-shepard/shepard-client";
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
const descendingSelected = ref(false);
const orderBySelected = ref<string>(GetAllDataObjectsOrderByEnum.CreatedAt);
const sizeSelected = ref(25);

const totalRows = computed(() => {
  if (props.maxObjects) {
    return props.maxObjects;
  } else if (
    dataObjects.value &&
    dataObjects.value.length < sizeSelected.value
  ) {
    return currentPage.value * sizeSelected.value;
  }
  return (currentPage.value + 1) * sizeSelected.value;
});

function filterChanged(options: FilterChangedData) {
  currentPage.value = options.currentPage;
  sizeSelected.value = options.currentSize;
  descendingSelected.value = options.descending;
  orderBySelected.value = options.orderBy;
  retrieveDataObjects();
}

function retrieveDataObjects(page?: number) {
  const nextPage = page || currentPage.value;
  const nextOrderBy =
    orderBySelected.value as keyof typeof GetAllDataObjectsOrderByEnum as GetAllDataObjectsOrderByEnum;
  DataObjectService?.getAllDataObjects({
    collectionId: props.currentCollectionId,
    parentId: props.parentId,
    predecessorId: props.predecessorId,
    successorId: props.successorId,
    size: sizeSelected.value,
    orderBy: nextOrderBy,
    orderDesc: descendingSelected.value,
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
      :default-size="sizeSelected"
      :default-descending="descendingSelected"
      :default-order-by="orderBySelected"
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
