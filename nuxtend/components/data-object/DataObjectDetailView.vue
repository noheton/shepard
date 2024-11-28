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
  <v-container v-if="dataObject" fluid class="pt-0">
    <v-row>
      <DataObjectTitle :data-object="dataObject" />
    </v-row>
    <v-row>
      <LayoutComponentsExpansionPanels>
        <LayoutComponentsExpansionPanelItem title="Description">
          <DataObjectDescription :data-object="dataObject" />
        </LayoutComponentsExpansionPanelItem>
        <LayoutComponentsExpansionPanelItem title="Attributes">
          <DataObjectAttributes :data-object="dataObject" />
        </LayoutComponentsExpansionPanelItem>
        <LayoutComponentsExpansionPanelItem title="Lab Journal">
          <DataObjectLabJournal :data-object="dataObject" />
        </LayoutComponentsExpansionPanelItem>
      </LayoutComponentsExpansionPanels>
    </v-row>
  </v-container>
</template>
