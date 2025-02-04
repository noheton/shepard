<script setup lang="ts">
import EditDataObjectDescriptionDialog from "~/components/context/data-object/edit/EditDataObjectDescriptionDialog.vue";
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

const showAttributeEditDialog = ref(false);
const showDescriptionEditDialog = ref(false);
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
          <Breadcrumbs
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
              <TitleAndMetadataDisplay
                :entity="dataObject"
                id-label="Data Object ID"
              />
            </v-row>
            <v-row no-gutters>
              <ExpansionPanels>
                <ExpansionPanelItem title="Description">
                  <DescriptionDisplay :entity="dataObject" />
                  <template #append>
                    <ExpansionPanelTitleButton
                      text="Edit"
                      icon="mdi-pencil-outline"
                      @click="() => (showDescriptionEditDialog = true)"
                    />
                    <EditDataObjectDescriptionDialog
                      v-if="showDescriptionEditDialog"
                      v-model:show-dialog="showDescriptionEditDialog"
                      :collection-id="collectionId"
                      :data-object-id="dataObjectId"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  title="Attributes"
                  :count="Object.keys(dataObject.attributes ?? {}).length"
                >
                  <AttributesDisplay :entity="dataObject" />
                  <template #append>
                    <ExpansionPanelTitleButton
                      text="Add/Edit"
                      icon="mdi-plus-circle"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                    <EditDataObjectAttributesDialog
                      v-if="showAttributeEditDialog"
                      v-model:show-dialog="showAttributeEditDialog"
                      :collection-id="collectionId"
                      :data-object-id="dataObjectId"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  title="Lab Journal"
                  :count="numberOfLabJournalEntries"
                >
                  <div class="pt-4">
                    <DataObjectLabJournalEntryList
                      :collection-id="collectionId"
                      :data-object-id="dataObject.id"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem title="Data" :count="dataReferences.length">
                  <DataObjectDataReferencesTable
                    :data-references="dataReferences"
                  />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  title="Relationships"
                  :count="relatedEntities.length"
                >
                  <DataObjectRelationshipsTable
                    :related-entities="relatedEntities"
                  />
                </ExpansionPanelItem>
              </ExpansionPanels>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
    </v-container>
  </div>
</template>
