<script setup lang="ts">
import { DataObjectApi, type DataObject } from "@dlr-shepard/backend-client";

interface DataObjectDetailViewProps {
  collectionId: number;
  dataObjectId: number | undefined;
}
const props = defineProps<DataObjectDetailViewProps>();

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

fetchDataObjectDetails();
</script>

<template>
  <v-container v-if="dataObject" fluid>
    <v-row>
      <DataObjectTitle :data-object="dataObject" />
    </v-row>
    <v-row>
      <v-expansion-panels variant="accordion">
        <v-expansion-panel title="Description">
          <v-expansion-panel-text>
            <DataObjectDescription :data-object="dataObject" />
          </v-expansion-panel-text>
        </v-expansion-panel>
        <v-expansion-panel title="Attributes">
          <v-expansion-panel-text>
            <DataObjectAttributes :data-object="dataObject" />
          </v-expansion-panel-text>
        </v-expansion-panel>
        <v-expansion-panel title="Lab Journal">
          <v-expansion-panel-text>
            <DataObjectLabJournal :data-object="dataObject" />
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
    </v-row>
  </v-container>
</template>
