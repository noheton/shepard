<script setup lang="ts">
import type { CollectionRouteParams } from "~/components/collection/collectionUtils";
import {
  collectionsPath,
  dataObjectsPathFragment,
} from "../../../../utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

// We cast this because this page will only be invoked with a data object id.
const { collectionId, dataObjectId } =
  routeParams.value as CollectionRouteParams & { dataObjectId: number };

const { collection } = useFetchCollection(collectionId);
const { dataObject } = useFetchDataObject(collectionId, dataObjectId);
const { dataReferences } = useDataReferencesByDataObject(
  collectionId,
  dataObjectId,
);
const { relatedEntities } = useRelatedEntities(collectionId, dataObjectId);
const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height" max-width="1000px">
      <v-row
        v-if="
          !!collection && !!dataObject && !!dataReferences && !!relatedEntities
        "
        no-gutters
      >
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
          <v-container fluid class="pa-0" max-width="1000px">
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
                  <div class="pt-4">
                    <LabJournalEntryList
                      :collection-id="collectionId"
                      :data-object-id="dataObject.id"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Data"
                  :count="dataReferences.length"
                >
                  <DataObjectDataReferencesTable
                    :data-references="dataReferences"
                  />
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Relationships"
                  :count="relatedEntities.length"
                >
                  <DataObjectRelationshipsTable
                    :related-entities="relatedEntities"
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
