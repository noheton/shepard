<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

interface DataObjectDetailViewProps {
  collectionId: number;
  dataObject: DataObject;
}
defineProps<DataObjectDetailViewProps>();
</script>

<template>
  <v-container v-if="dataObject" fluid class="pa-0">
    <v-row no-gutters>
      <EntityTitle :entity="dataObject" id-label="Data Object ID" />
    </v-row>
    <v-row no-gutters>
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
