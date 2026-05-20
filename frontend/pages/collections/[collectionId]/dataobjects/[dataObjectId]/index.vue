<script lang="ts" setup>
import DataObjectFileUpload from "~/components/context/data-object/upload-data/DataObjectFileUpload.vue";
import DataObjectNotebooksPane from "~/components/context/lab-journal/DataObjectNotebooksPane.vue";
import GitReferencesPane from "~/components/context/dataobject/GitReferencesPane.vue";
import VideoStreamReferencesPane from "~/components/context/dataobject/VideoStreamReferencesPane.vue";
import AddRelationshipDialog from "~/components/context/display-components/relationships/add-dialog/AddRelationshipDialog.vue";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import { DataObjectApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
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
const dataObjectApi = useShepardApi(DataObjectApi);

const showAttributeEditDialog = ref(false);
const showCreateDataReferenceDialog = ref(false);
const showAddRelationshipDialog = ref(false);

// Provenance sub-view: structured log (default) vs force-directed graph.
const provView = ref<"log" | "graph">("log");

// ── Inline description editing ────────────────────────────────────────────
const descEditActive = ref(false);
const descDraft = ref("");
const descStatusDraft = ref<string | null>(null);
const descSaving = ref(false);

function startDescEdit() {
  descDraft.value = dataObject.value?.description ?? "";
  descStatusDraft.value = dataObject.value?.status ?? null;
  descEditActive.value = true;
}

function cancelDescEdit() {
  descEditActive.value = false;
}

async function saveDescEdit() {
  if (!dataObject.value) return;
  descSaving.value = true;
  try {
    await dataObjectApi.value.updateDataObject({
      collectionId,
      dataObjectId,
      dataObject: {
        name: dataObject.value.name,
        description: descDraft.value,
        status: descStatusDraft.value ?? undefined,
        attributes: dataObject.value.attributes ?? {},
        parentId: dataObject.value.parentId,
        predecessorIds: dataObject.value.predecessorIds ?? [],
      },
    });
    emitSuccess(`Description updated`);
    handleDataObjectUpdate();
    descEditActive.value = false;
  } catch (e) {
    handleError(e, "updating description");
  } finally {
    descSaving.value = false;
  }
}

watch(dataObject, () => {
  useHead({
    title: dataObject.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1400px">
    <v-container class="pa-0 fill-height" fluid>
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
          <v-container class="pa-0" fluid>
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
            <!-- Always-visible: Description with inline edit. -->
            <section class="page-section">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Description</div>
                <v-btn
                  v-if="isAllowedToEditCollection && !descEditActive"
                  variant="text"
                  density="comfortable"
                  size="small"
                  prepend-icon="mdi-pencil-outline"
                  @click="startDescEdit"
                >Edit</v-btn>
              </div>
              <RichTextEditor
                v-if="descEditActive"
                v-model="descDraft"
                :is-editable="true"
              />
              <DescriptionDisplay v-else :entity="dataObject" />
              <div v-if="descEditActive" class="d-flex align-center ga-2 mt-3">
                <v-select
                  v-model="descStatusDraft"
                  label="Status"
                  :items="['DRAFT', 'IN_REVIEW', 'READY', 'PUBLISHED', 'ARCHIVED']"
                  density="compact"
                  clearable
                  hide-details
                  style="max-width: 200px"
                />
                <v-spacer />
                <v-btn variant="text" size="small" @click="cancelDescEdit">Cancel</v-btn>
                <v-btn
                  variant="flat"
                  color="primary"
                  size="small"
                  :loading="descSaving"
                  @click="saveDescEdit"
                >Save</v-btn>
              </div>
            </section>

            <!-- Always-visible: Semantic Annotation chips. -->
            <section class="page-section">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Semantic Annotations</div>
                <AddAnnotationButton
                  v-if="isAllowedToEditCollection"
                  :annotated="
                    new AnnotatedDataObject(collectionId, dataObjectId)
                  "
                />
              </div>
              <SemanticAnnotationList
                :annotated="
                  new AnnotatedDataObject(collection.id, dataObject.id)
                "
                :can-delete="!!isAllowedToEditCollection"
              />
            </section>

            <v-row no-gutters>
              <ExpansionPanels :default-open="[2]">
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
                  <template v-if="dataObject.appId">
                    <v-divider class="my-6" />
                    <GitReferencesPane :data-object-app-id="dataObject.appId" />
                    <v-divider class="my-6" />
                    <VideoStreamReferencesPane
                      :data-object-app-id="dataObject.appId"
                      :can-upload="!!isAllowedToEditCollection"
                    />
                  </template>
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
                <!-- Provenance: two views — a structured time-based log
                     (default, easier to read) and the force-directed
                     graph (eye-candy, second tab). Both render the same
                     /v2/provenance/activities data; the user picks
                     whichever shape they want. -->
                <ExpansionPanelItem title="Provenance">
                  <v-tabs v-model="provView" density="compact" color="primary" class="pb-2">
                    <v-tab value="log">
                      <v-icon size="small" class="me-1">mdi-format-list-bulleted</v-icon>
                      Log
                    </v-tab>
                    <v-tab value="graph">
                      <v-icon size="small" class="me-1">mdi-graph-outline</v-icon>
                      Graph
                    </v-tab>
                  </v-tabs>
                  <div class="pt-2 pb-2">
                    <DataObjectProvLog
                      v-if="provView === 'log' && dataObject.appId"
                      :target-app-id="dataObject.appId"
                    />
                    <DataObjectProvGraph
                      v-else-if="provView === 'graph'"
                      :data-object="dataObject"
                      :collection-id="collectionId"
                    />
                  </div>
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

<style lang="scss" scoped>
.page-section {
  margin-bottom: 24px;
}
.page-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-left: 32px;
}
</style>
