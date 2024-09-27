<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";

interface CollectionDetailViewProps {
  collectionId: number;
  dataObjectId: number | undefined;
}
const props = defineProps<CollectionDetailViewProps>();

const dataObject = ref<DataObject | undefined>(undefined);

async function fetchDataObjectDetails() {
  if (props.dataObjectId)
    createApiInstance(DataObjectApi)
      .getDataObject({
        collectionId: props.collectionId,
        dataObjectId: props.dataObjectId,
      })
      .then(response => {
        dataObject.value = response;
      })
      .catch(error => {
        handleError(error, "getDataObject");
      });
}

watchEffect(() => {
  if (props.dataObjectId) {
    fetchDataObjectDetails();
  } else {
    dataObject.value = undefined;
  }
});
</script>

<template>
  <v-container fluid>
    <v-row>
      <v-col cols="12">
        Details for DataObject in collection
        {{ props.collectionId }}
      </v-col>
    </v-row>
  </v-container>
  <v-container v-if="dataObject" fluid>
    <v-row>
      <v-col cols="4">Collection ID: {{ dataObject.collectionId }}</v-col>
      <v-col cols="4">
        Created at: {{ dataObject.createdAt?.toDateString() }} by
        {{ dataObject.createdBy }}
      </v-col>
      <v-col v-if="dataObject.updatedAt" cols="4">
        Updated at: {{ dataObject.updatedAt?.toDateString() }} by
        {{ dataObject.updatedBy }}
      </v-col>
    </v-row>
    <v-row>
      <v-col cols="12">Description:</v-col>
      <v-col cols="12">
        <span>{{ dataObject.description }}</span>
      </v-col>
    </v-row>
  </v-container>
</template>
