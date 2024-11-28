<script setup lang="ts">
import {
  type Collection,
  CollectionApi,
  type DataObject,
  DataObjectApi,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import {
  collectionsPath,
  dataObjectsPathFragment,
} from "../../../../utils/constants";

definePageMeta({ layout: "collection" });

const route = useRoute();
const collectionId = parseInt(route.params.collectionId as string);
const dataObjectId = parseInt(route.params.dataObjectId as string);

const collection = ref<Collection | undefined>(undefined);
const dataObject = ref<DataObject | undefined>(undefined);

function fetchCollection() {
  createApiInstance(CollectionApi)
    .getCollection({ collectionId })
    .then(response => {
      collection.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

async function fetchDataObject() {
  createApiInstance(DataObjectApi)
    .getDataObject({
      collectionId: collectionId,
      dataObjectId: dataObjectId,
    })
    .then(response => {
      dataObject.value = response;
    })
    .catch(error => {
      handleError(error, "getDataObject");
    });
}

fetchCollection();
fetchDataObject();
</script>

<template>
  <div v-if="!!collection && !!dataObject">
    <LayoutComponentsShepardBreadcrumbs
      :items="[
        {
          title: 'Collections',
          to: collectionsPath,
        },
        {
          title: `Collection '${collection.name}'`,
          to: collectionsPath + collection.id,
        },
        {
          title: dataObject.name,
          to:
            collectionsPath +
            collectionId +
            dataObjectsPathFragment +
            dataObjectId,
        },
      ]"
    />
    <v-container fluid class="pa-0">
      <v-row no-gutters>
        <EntityTitle :entity="dataObject" id-label="Data Object ID" />
      </v-row>
      <v-row no-gutters>
        <LayoutComponentsExpansionPanels>
          <LayoutComponentsExpansionPanelItem title="Description">
            <EntityDescription :entity="dataObject" />
          </LayoutComponentsExpansionPanelItem>
          <LayoutComponentsExpansionPanelItem title="Attributes">
            <EntityAttributes :entity="dataObject" />
          </LayoutComponentsExpansionPanelItem>
          <LayoutComponentsExpansionPanelItem title="Lab Journal">
            <DataObjectLabJournal :data-object="dataObject" />
          </LayoutComponentsExpansionPanelItem>
        </LayoutComponentsExpansionPanels>
      </v-row>
    </v-container>
  </div>
  <LayoutComponentsCenteredLoadingSpinner v-else />
</template>
