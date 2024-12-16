<script setup lang="ts">
import type { Collection, ResponseError } from "@dlr-shepard/backend-client";
import { CollectionApi, DataObjectApi } from "@dlr-shepard/backend-client";
import { collectionsPath } from "../../../utils/constants";

definePageMeta({ layout: "collection" });

const route = useRoute();
const collectionId = parseInt(route.params.collectionId as string);

const numberOfLabJournalEntries = ref<number | undefined>(undefined);
const collection = ref<Collection | undefined>(undefined);

const dataObjectsMap = ref<Map<number, string>>(new Map<number, string>());

function fetchCollection(collectionId: number) {
  createApiInstance(CollectionApi)
    .getCollection({ collectionId })
    .then(response => {
      collection.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching collection");
    });
}

function fetchDataObjectsByCollectionId(collectionId: number) {
  createApiInstance(DataObjectApi)
    .getAllDataObjects({ collectionId })
    .then(response => {
      response.forEach(dataObject => {
        dataObjectsMap.value.set(dataObject.id, dataObject.name);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching dataobjects");
    });
}

async function onLabJournalCountChanged(count: number) {
  numberOfLabJournalEntries.value = count;
}

fetchCollection(collectionId);
fetchDataObjectsByCollectionId(collectionId);
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height">
      <v-row v-if="!!collection" no-gutters>
        <v-col cols="12">
          <LayoutComponentsShepardBreadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `Collection '${collection.name}'`,
                to: collectionsPath + collectionId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container fluid class="pa-0">
            <v-row no-gutters>
              <EntityTitle :entity="collection" id-label="Collection ID" />
            </v-row>
            <v-row no-gutters>
              <EntityExpansionPanels>
                <EntityExpansionPanelItem title="Description">
                  <EntityDescription :entity="collection" />
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Attributes"
                  :count="Object.keys(collection.attributes ?? {}).length"
                >
                  <EntityAttributes :entity="collection" />
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Lab Journal"
                  :count="numberOfLabJournalEntries"
                >
                  <div class="pt-4">
                    <CollectionLabJournalEntryList
                      :collection-id="collectionId"
                      :data-object-map="dataObjectsMap"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
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
