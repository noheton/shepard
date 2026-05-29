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

const {
  collection,
  isAllowedToEditCollection,
  isError: collectionFetchError,
} = useFetchCollection(collectionId);
const {
  dataObject,
  isLoading: dataObjectLoading,
  isError: dataObjectFetchError,
} = useFetchDataObject(collectionId, dataObjectId);
const { dataReferences } = useDataReferencesByDataObject(
  collectionId,
  dataObjectId,
);
const { relatedEntities } = useRelatedEntities(collectionId, dataObjectId);

// Graceful degradation: show the page body when the core DataObject is loaded,
// even if secondary fetches (collection metadata, references, relationships)
// fail or are still in-flight. A dismissable alert surfaces partial failures
// so the user knows something may be incomplete.
const partialFetchError = ref(false);
const dismissFetchError = () => {
  partialFetchError.value = false;
};

// Treat collection or dataObject load failure as a partial page error.
// We surface the alert and still render whatever loaded.
watch([collectionFetchError, dataObjectFetchError], ([collErr, doErr]) => {
  if (collErr || doErr) partialFetchError.value = true;
});

// Resolved references/relationships: use empty arrays as safe fallback when
// the async fetch hasn't resolved yet (avoids blocking the whole page).
const safeDataReferences = computed(() => dataReferences.value ?? []);
const safeRelatedEntities = computed(() => relatedEntities.value ?? []);

// The page renders its body once the DataObject itself has loaded (or errored
// with data from a prior successful load). Secondary data (collection, refs,
// relationships) degrades gracefully.
const pageReady = computed(
  () => !!dataObject.value || (!dataObjectLoading.value && dataObjectFetchError.value),
);
const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();
// UX Pattern D: counts surfaced on every reference-bearing panel/section so the
// title alone tells the user whether the panel is worth expanding.
const {
  counter: numberOfNotebookEntries,
  updateCount: onNotebookCountChanged,
} = useCounter();
const numberOfSemanticAnnotations = ref<number | undefined>(undefined);
function onAnnotationsLoaded(annotations: { length: number }) {
  numberOfSemanticAnnotations.value = annotations.length;
}
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

// UX Pattern F (2026-05-24): reactive title with collection breadcrumb.
// Pattern: "<DataObject.name> · <Collection.name> — shepard" so the
// browser-tab strip stays readable with multiple DOs open.
useHead({
  title: () => {
    const doName = dataObject.value?.name;
    const collName = collection.value?.name;
    if (doName && collName) return `${doName} · ${collName} — shepard`;
    if (doName) return `${doName} — shepard`;
    return "DataObject — shepard";
  },
});

// LIC1 (FAIR-1): defensive computed accessors mirroring the collection
// detail page — the generated client model may not yet expose these fields
// even though the wire payload carries them.
const dataObjectLicense = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { license?: string | null }).license;
  return raw ?? null;
});
const dataObjectAccessRights = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { accessRights?: string | null })
    .accessRights;
  return raw ?? null;
});
</script>

<template>
  <div style="max-width: 1400px">
    <v-container class="pa-0 fill-height" fluid>
      <!-- Graceful degradation: if any sub-fetch failed, show a dismissable
           warning at the top. The page body still renders with whatever data
           did load — secondary data (references, relationships) falls back to
           empty arrays rather than blocking the whole page on one failed fetch.
           UX-WALK-2026-05-29-06 -->
      <v-alert
        v-if="partialFetchError"
        type="warning"
        variant="tonal"
        closable
        class="mb-4"
        @click:close="dismissFetchError"
      >
        Some information couldn't be loaded. The page may be incomplete.
      </v-alert>

      <!-- Core loading state: show spinner only while the DataObject itself is
           still in-flight. Once it resolves (or errors), show the page body. -->
      <CenteredLoadingSpinner v-if="!pageReady" />

      <v-row
        v-else-if="pageReady && !!dataObject"
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
                title: collection?.name ?? `Collection ${collectionId}`,
                to: collectionsPath + collectionId,
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
            <!-- LIC1: FAIR metadata strip — license + accessRights. Same
                 affordance pattern as the Collection detail page. -->
            <v-row
              v-if="dataObjectLicense || dataObjectAccessRights"
              no-gutters
              class="pb-3 ga-2 align-center"
            >
              <LicenseChip
                v-if="dataObjectLicense"
                :license="dataObjectLicense"
              />
              <AccessRightsChip
                v-if="dataObjectAccessRights"
                :access-rights="dataObjectAccessRights"
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
            <!-- Always-visible: Description with inline edit. UI-017: when
                 edit is engaged, the surrounding section gets an outline + a
                 small "Editing description" label so the user can see at a
                 glance WHICH panel they are editing (the editor pops below the
                 head, so the visual cue closes the affordance gap). -->
            <section
              class="page-section description-section"
              :class="{ 'description-editing': descEditActive }"
              :data-testid="
                descEditActive ? 'description-editing' : 'description-static'
              "
            >
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Description</div>
                <span
                  v-if="descEditActive"
                  class="text-caption text-primary editing-label"
                  data-testid="description-editing-label"
                >
                  <v-icon size="x-small" class="mr-1">mdi-pencil-outline</v-icon>
                  Editing description
                </span>
                <v-btn
                  v-if="isAllowedToEditCollection && !descEditActive"
                  variant="text"
                  density="comfortable"
                  size="small"
                  prepend-icon="mdi-pencil-outline"
                  aria-label="Edit description"
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
                <div class="text-h5 text-textbody1">
                  Semantic Annotations
                  <!-- UX Pattern D: low-emphasis count badge — matches the
                       ExpansionPanelItem convention so the section title is
                       scannable without expanding/scrolling. -->
                  <span
                    v-if="numberOfSemanticAnnotations !== undefined"
                    class="text-low-emphasis ml-1"
                    data-testid="semantic-annotations-count"
                  >({{ numberOfSemanticAnnotations }})</span>
                </div>
                <AddAnnotationButton
                  v-if="isAllowedToEditCollection"
                  :annotated="
                    new AnnotatedDataObject(collectionId, dataObjectId)
                  "
                />
              </div>
              <SemanticAnnotationList
                :annotated="
                  new AnnotatedDataObject(collection?.id ?? collectionId, dataObject.id)
                "
                :can-delete="!!isAllowedToEditCollection"
                @annotations="onAnnotationsLoaded"
              />
            </section>

            <v-row no-gutters>
              <ExpansionPanels :default-open="[2, 3]">
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
                  :count="safeDataReferences.length"
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
                      :collection-id="collection?.id ?? collectionId"
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
                    :data-references="safeDataReferences"
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
                  :count="safeRelatedEntities.length"
                  title="Relationships"
                >
                  <DataObjectRelationshipsTable
                    :collection-id="collectionId"
                    :data-object-id="dataObjectId"
                    :is-allowed-to-edit-collection="
                      isAllowedToEditCollection ?? false
                    "
                    :related-entities="safeRelatedEntities"
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
                  :count="numberOfNotebookEntries"
                  title="Jupyter Notebooks"
                >
                  <DataObjectNotebooksPane
                    :data-object-app-id="dataObject.appId"
                    @number-of-entries-changed="onNotebookCountChanged"
                  />
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

      <!-- Fallback: DataObject fetch failed entirely (not just partial failure) -->
      <v-row
        v-else-if="pageReady && !dataObject"
        no-gutters
        class="justify-center align-center fill-height"
      >
        <v-col cols="12" sm="8" md="6" class="text-center pa-8">
          <v-icon size="64" color="error" class="mb-4">mdi-alert-circle-outline</v-icon>
          <div class="text-h6 mb-2">DataObject could not be loaded</div>
          <div class="text-body-2 text-medium-emphasis">
            This DataObject may have been deleted, or you may not have permission to view it.
          </div>
        </v-col>
      </v-row>
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
// UI-017: visual cue for inline description edit. A subtle 2px primary-tinted
// outline + soft tint frames the section being edited so the user can see
// what's editable without hunting for the editor.
.description-section {
  border-radius: 6px;
  transition: outline-color 120ms ease, background-color 120ms ease;
  outline: 2px solid transparent;
}
.description-editing {
  outline: 2px solid rgba(var(--v-theme-primary), 0.4);
  background-color: rgba(var(--v-theme-primary), 0.03);
  padding: 8px 0;
}
.editing-label {
  display: inline-flex;
  align-items: center;
  font-weight: 500;
  letter-spacing: 0.02em;
}
</style>
