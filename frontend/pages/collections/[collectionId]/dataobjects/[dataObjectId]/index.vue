<script lang="ts" setup>
import EditDataObjectDescriptionDialog from "~/components/context/data-object/edit-dialog/EditDataObjectDescriptionDialog.vue";
import DataObjectFileUpload from "~/components/context/data-object/upload-data/DataObjectFileUpload.vue";
import DataObjectNotebooksPane from "~/components/context/lab-journal/DataObjectNotebooksPane.vue";
import GitReferencesPane from "~/components/context/dataobject/GitReferencesPane.vue";
import VideoStreamReferencesPane from "~/components/context/dataobject/VideoStreamReferencesPane.vue";
import AddRelationshipDialog from "~/components/context/display-components/relationships/add-dialog/AddRelationshipDialog.vue";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

// We cast this because this page will only be invoked with a data object id.
const { collectionId, dataObjectId } =
  routeParams.value as CollectionRouteParams & { dataObjectId: number };

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionId);
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
const showCreateDataReferenceDialog = ref(false);
const showAddRelationshipDialog = ref(false);

watch(dataObject, () => {
  useHead({
    title: dataObject.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
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
                title: `${collection.name}`,
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
          <v-container class="pa-0" fluid max-width="1000px">
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="dataObject"
                id-label="Data Object ID"
              />
            </v-row>
            <v-row
              v-if="dataObject.appId && isAllowedToEditCollection"
              no-gutters
              class="justify-end pb-2"
            >
              <PublishButton
                entity-kind="data-objects"
                :entity-app-id="dataObject.appId"
                :entity-name="dataObject.name"
              />
            </v-row>
            <v-row no-gutters>
              <ExpansionPanels>
                <ExpansionPanelItem title="Description">
                  <DescriptionDisplay :entity="dataObject" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-pencil-outline"
                      text="Edit"
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
                <ExpansionPanelItem title="Semantic Annotations">
                  <template v-if="isAllowedToEditCollection" #append>
                    <AddAnnotationButton
                      :annotated="
                        new AnnotatedDataObject(collectionId, dataObjectId)
                      "
                    />
                  </template>
                  <SemanticAnnotationList
                    :annotated="
                      new AnnotatedDataObject(collection.id, dataObject.id)
                    "
                    :can-delete="!!isAllowedToEditCollection"
                  />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="Object.keys(dataObject.attributes ?? {}).length"
                  title="Attributes"
                >
                  <AttributesDisplay :entity="dataObject" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add/Edit"
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
                  :count="numberOfLabJournalEntries"
                  title="Lab Journal"
                >
                  <div class="pt-4">
                    <DataObjectLabJournalEntryList
                      :collection-id="collectionId"
                      :data-object-id="dataObject.id"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="dataReferences.length"
                  title="Data References"
                >
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add data reference"
                      @click="() => (showCreateDataReferenceDialog = true)"
                    />
                    <CreateDataReferenceDialog
                      v-if="showCreateDataReferenceDialog"
                      v-model:show-dialog="showCreateDataReferenceDialog"
                      :collection-id="collection.id"
                      :data-object-id="dataObject.id"
                    />
                  </template>
                  <DataObjectFileUpload
                    v-if="isAllowedToEditCollection"
                    :collection-id="collectionId"
                    :dataobject-id="dataObjectId"
                  />
                  <DataObjectDataReferencesTable
                    :collection-id="collectionId"
                    :data-object-id="dataObjectId"
                    :data-references="dataReferences"
                    :is-allowed-to-edit-collection="
                      isAllowedToEditCollection ?? false
                    "
                  />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="relatedEntities.length"
                  title="Relationships"
                >
                  <DataObjectRelationshipsTable
                    :collection-id="collectionId"
                    :data-object-id="dataObjectId"
                    :is-allowed-to-edit-collection="
                      isAllowedToEditCollection ?? false
                    "
                    :related-entities="relatedEntities"
                  />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add"
                      @click="() => (showAddRelationshipDialog = true)"
                    />
                    <AddRelationshipDialog
                      v-if="showAddRelationshipDialog"
                      v-model:show-dialog="showAddRelationshipDialog"
                      :collection-id="collectionId"
                      :data-object-id="dataObjectId"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="dataObject.appId"
                  title="Jupyter Notebooks"
                >
                  <DataObjectNotebooksPane :data-object-app-id="dataObject.appId" />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="dataObject.appId"
                  title="Git References"
                >
                  <GitReferencesPane :data-object-app-id="dataObject.appId" />
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="dataObject.appId"
                  title="Video References"
                >
                  <VideoStreamReferencesPane :data-object-app-id="dataObject.appId" />
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
