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
const numberOfLabJournalEntries = ref<number | undefined>(undefined);

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

async function onLabJournalCountChanged(count: number) {
  numberOfLabJournalEntries.value = count;
}

fetchCollection();
fetchDataObject();
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height">
      <v-row v-if="!!collection && !!dataObject" no-gutters>
        <v-col cols="12">
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
        </v-col>
        <v-col cols="12">
          <v-container fluid class="pa-0">
            <v-row no-gutters>
              <EntityTitle :entity="dataObject" id-label="Data Object ID" />
            </v-row>
            <v-row no-gutters>
              <EntityExpansionPanels>
                <EntityExpansionPanelItem title="Description">
                  <EntityDescription :entity="dataObject" />
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Attributes"
                  :count="Object.keys(dataObject.attributes ?? {}).length"
                >
                  <EntityAttributes :entity="dataObject" />
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Lab Journal"
                  :count="numberOfLabJournalEntries"
                >
                  <LabJournalList
                    :collection-id="collectionId"
                    :data-object-id="dataObject.id"
                    @number-of-entries-changed="onLabJournalCountChanged"
                  />
                </EntityExpansionPanelItem>
              </EntityExpansionPanels>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <LayoutComponentsCenteredLoadingSpinner v-else />
    </v-container>
  </div>
</template>
