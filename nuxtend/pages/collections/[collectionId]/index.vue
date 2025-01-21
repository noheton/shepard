<script setup lang="ts">
import { collectionsPath } from "../../../utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();

const { collection } = useFetchCollection(routeParams.value.collectionId);
const { dataObjectsMap } = useFetchDataObjectMapByCollection(
  routeParams.value.collectionId,
);
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
                to: collectionsPath + routeParams.collectionId,
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
                  <template #append>
                    <EntityAttributesEditDialog :entity="collection" />
                  </template>
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Lab Journal"
                  :count="numberOfLabJournalEntries"
                >
                  <div class="pt-4">
                    <CollectionLabJournalEntryList
                      :collection-id="routeParams.collectionId"
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
