<script setup lang="ts">
import DataObjectListItem from "@/components/dataobjects/DataObjectListItem.vue";
import type { DataObject, ResponseError } from "@/generated/openapi";
import DataObjectService from "@/services/dataObjectService";
import { handleError } from "@/utils/error-handling";
import { onMounted, ref } from "vue";

const props = defineProps({
  collectionId: {
    type: Number,
    required: true,
  },
  dataObjectId: {
    type: Number,
    required: true,
  },
});

const dataObject = ref<DataObject>();

function retrieveDataObject() {
  DataObjectService.getDataObject({
    collectionId: props.collectionId,
    dataObjectId: props.dataObjectId,
  })
    .then(response => {
      dataObject.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching data object");
    });
}

onMounted(() => {
  retrieveDataObject();
});
</script>

<template>
  <b-card no-body>
    <b-list-group>
      <DataObjectListItem v-if="dataObject" :data-object="dataObject" />
    </b-list-group>
  </b-card>
</template>
