<script lang="ts" setup>
import DataObjectFileUpload from "~/components/context/data-object/upload-data/DataObjectFileUpload.vue";
// REF-UNIFIED-TABLE: GitReferencesPane, VideoStreamReferencesPane, HdfReferencesPane removed
// from this page; they now live in frontend/components/context/dataobject/legacy/ for reuse.
// J1c retirement (2026-05-29): DataObjectNotebooksPane is gone too — notebooks
// now appear as rows in the unified data-references table with a notebook icon
// and a per-row "Open in JupyterHub" action (J1e).
import AddRelationshipDialog from "~/components/context/display-components/relationships/add-dialog/AddRelationshipDialog.vue";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import PublicationStatusBadge from "~/components/context/publish/PublicationStatusBadge.vue";
import { DataObjectApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { collectionsPath, dataObjectsPathFragment } from "~/utils/constants";
import { useFetchTypedPredecessors } from "~/composables/context/useFetchTypedPredecessors";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import AncestorChainPanel from "~/components/context/data-object/AncestorChainPanel.vue";
import EntityToolsMenu from "~/components/context/tools/EntityToolsMenu.vue";
import { useFetchGitReferences } from "~/composables/context/useFetchGitReferences";
import { useFetchVideoStreamReferences } from "~/composables/context/useFetchVideoStreamReferences";
import { useFetchSingletonFileReferences } from "~/composables/context/useFetchSingletonFileReferences";
import {
  mapGitReferenceToDataTableElement,
  mapSingletonFileReferenceToDataTableElement,
  mapVideoReferenceToDataTableElement,
} from "~/components/context/display-components/data-references/dataTableElementMappingUtil";
import type { DataTableElement } from "~/components/context/display-components/data-references/dataTableElement";

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

// REF-UNIFIED-TABLE: extra items for Git/Video/HDF5 reference kinds.
// These composables are set up once dataObject.appId is available, since
// the new-kind endpoints use appId (string) rather than numeric id.
const extraReferenceItems = ref<DataTableElement[]>([]);

// Hold refs to sub-composable refresh functions so the panel can request re-fetch.
const refreshGitRefs = ref<(() => void) | null>(null);
const refreshVideoRefs = ref<(() => void | Promise<void>) | null>(null);
// REF-UNIFIED-TABLE-FR1B / J1c retirement: FR1b singletons (incl. notebooks)
// fetched via the additive /v2/files/by-data-object endpoint.
const refreshFr1bRefs = ref<(() => void | Promise<void>) | null>(null);

watch(
  () => dataObject.value?.appId,
  (appId) => {
    if (!appId) return;

    const gitComposable = useFetchGitReferences(appId);
    refreshGitRefs.value = gitComposable.refresh;

    const videoComposable = useFetchVideoStreamReferences(appId);
    refreshVideoRefs.value = videoComposable.refresh;

    const fr1bComposable = useFetchSingletonFileReferences(appId);
    refreshFr1bRefs.value = fr1bComposable.refresh;

    // Reactively derive extraReferenceItems from the three composable states.
    watchEffect(() => {
      extraReferenceItems.value = [
        ...gitComposable.gitReferences.value.map(mapGitReferenceToDataTableElement),
        ...videoComposable.references.value.map(mapVideoReferenceToDataTableElement),
        ...fr1bComposable.references.value.map(mapSingletonFileReferenceToDataTableElement),
      ];
    });
  },
  { immediate: true },
);

function refreshExtraReferences() {
  refreshGitRefs.value?.();
  refreshVideoRefs.value?.();
  refreshFr1bRefs.value?.();
}

/** Total count for the "Data References" panel badge: legacy + new kinds. */
const totalReferenceCount = computed(
  () => (dataReferences.value?.length ?? 0) + extraReferenceItems.value.length,
);

// PROV1k: fetch typed predecessor summaries from the v2 detail endpoint.
// Best-effort: empty when the DataObject has no typed predecessors or backend
// predates PROV1k. We watch dataObject.appId + collection.appId reactively.
const typedPredecessorsRef = computed(() => {
  if (!collection.value?.appId || !dataObject.value?.appId) return null;
  return { collectionAppId: collection.value.appId, dataObjectAppId: dataObject.value.appId };
});
const typedPredecessors = ref<
  Array<{
    predecessorAppId: string;
    predecessorId: number;
    predecessorName: string;
    predecessorStatus: string | null;
    relationshipType: string;
  }>
>([]);
watch(
  typedPredecessorsRef,
  async val => {
    if (!val) return;
    const { typedPredecessors: tpFetched } = useFetchTypedPredecessors(
      val.collectionAppId,
      val.dataObjectAppId,
    );
    watch(tpFetched, v => { typedPredecessors.value = v; }, { immediate: true });
  },
  { immediate: true },
);

/** Look up the stored relationship type for a predecessor by its numeric id. */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
function predecessorRelationshipType(predecessorId: number): string | null {
  const match = typedPredecessors.value.find(tp => tp.predecessorId === predecessorId);
  return match?.relationshipType ?? null;
}

/** PROV1k: Map of predecessor id → relationship type, passed to DataObjectRelationshipsTable. */
const predecessorRelationshipTypesMap = computed<Map<number, string>>(() => {
  const map = new Map<number, string>();
  for (const tp of typedPredecessors.value) {
    // Only add non-default types to keep the map sparse; the chip component handles null gracefully.
    if (tp.relationshipType && tp.relationshipType !== "prov:wasInformedBy") {
      map.set(tp.predecessorId, tp.relationshipType);
    }
  }
  return map;
});
const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();
// J1c retirement (2026-05-29): the dedicated notebooks panel + its counter
// are gone. Notebooks now live as rows in the unified data-references table.
const numberOfSemanticAnnotations = ref<number | undefined>(undefined);
function onAnnotationsLoaded(annotations: { length: number }) {
  numberOfSemanticAnnotations.value = annotations.length;
}
const dataObjectApi = useShepardApi(DataObjectApi);

const showAttributeEditDialog = ref(false);
const showCreateDataReferenceDialog = ref(false);
const showAddRelationshipDialog = ref(false);

// SEMA-V6-017: unified "Annotate" button replaces the legacy "Add annotation" button.
// Both basic and advanced mode show this button (superset rule — never hide basic content).
// Advanced mode additionally shows the legacy "Add raw attribute" button with deprecation note.
const showAnnotationDialog = ref(false);
const { advancedMode } = useAdvancedMode();

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

// LIC1 (FAIR-1): defensive computed accessors — the generated client model may
// not yet expose these fields even though the wire payload carries them.
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

// FAIR2: createdByOrcid — server-stamped ORCID of the creating researcher.
// Read-only; displayed as a badge linking to orcid.org.
const dataObjectCreatedByOrcid = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { createdByOrcid?: string | null })
    .createdByOrcid;
  return raw ?? null;
});

// FAIR3: embargoEndDate — user-provided ISO-8601 end date for embargoed datasets.
// Editable when the user has Write permission on the collection.
const dataObjectEmbargoEndDate = computed<string | null>(() => {
  if (!dataObject.value) return null;
  const raw = (dataObject.value as unknown as { embargoEndDate?: string | null })
    .embargoEndDate;
  return raw ?? null;
});

// FAIR3: inline editing state for embargoEndDate.
const embargoEditActive = ref(false);
const embargoDraft = ref("");
const embargoSaving = ref(false);

function startEmbargoEdit() {
  embargoDraft.value = dataObjectEmbargoEndDate.value ?? "";
  embargoEditActive.value = true;
}

function cancelEmbargoEdit() {
  embargoEditActive.value = false;
}

async function saveEmbargoEdit() {
  if (!dataObject.value) return;
  embargoSaving.value = true;
  try {
    await dataObjectApi.value.updateDataObject({
      collectionId,
      dataObjectId,
      dataObject: {
        name: dataObject.value.name,
        description: dataObject.value.description,
        status: (dataObject.value as unknown as { status?: string }).status,
        attributes: dataObject.value.attributes ?? {},
        parentId: dataObject.value.parentId,
        predecessorIds: dataObject.value.predecessorIds ?? [],
        // FAIR3: pass embargoEndDate through the update. Null/empty clears it.
        ...(embargoDraft.value
          ? { embargoEndDate: embargoDraft.value }
          : { embargoEndDate: null }),
      } as Parameters<typeof dataObjectApi.value.updateDataObject>[0]["dataObject"],
    });
    emitSuccess("Embargo end date updated");
    handleDataObjectUpdate();
    embargoEditActive.value = false;
  } catch (e) {
    handleError(e, "updating embargo end date");
  } finally {
    embargoSaving.value = false;
  }
}
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
            <!-- LIC1/FAIR2/FAIR3/KIP1k: FAIR metadata strip — license + accessRights
                 + embargoEndDate + createdByOrcid + publication status. Same affordance
                 pattern as the Collection detail page. -->
            <v-row
              v-if="dataObjectLicense || dataObjectAccessRights || dataObjectEmbargoEndDate || dataObjectCreatedByOrcid || dataObject.appId"
              no-gutters
              class="pb-3 ga-2 align-center flex-wrap"
              data-testid="fair-metadata-strip"
            >
              <LicenseChip
                v-if="dataObjectLicense"
                :license="dataObjectLicense"
              />
              <AccessRightsChip
                v-if="dataObjectAccessRights"
                :access-rights="dataObjectAccessRights"
              />
              <!-- KIP1k: publication-status badge — informational only. -->
              <PublicationStatusBadge
                v-if="dataObject.appId"
                entity-kind="data-objects"
                :entity-app-id="dataObject.appId"
              />
              <!-- FAIR3: embargoEndDate — shown when set; editable by Write users. -->
              <span
                v-if="dataObjectEmbargoEndDate && !embargoEditActive"
                class="d-inline-flex align-center ga-1"
                data-testid="embargo-end-date-display"
              >
                <v-chip
                  size="small"
                  color="warning"
                  prepend-icon="mdi-lock-clock"
                  :text="`Embargo ends: ${dataObjectEmbargoEndDate}`"
                />
                <v-btn
                  v-if="isAllowedToEditCollection"
                  icon="mdi-pencil-outline"
                  variant="text"
                  size="x-small"
                  density="comfortable"
                  aria-label="Edit embargo end date"
                  data-testid="embargo-edit-btn"
                  @click="startEmbargoEdit"
                />
              </span>
              <!-- FAIR3 inline editor -->
              <span
                v-if="embargoEditActive"
                class="d-inline-flex align-center ga-2"
                data-testid="embargo-edit-form"
              >
                <v-text-field
                  v-model="embargoDraft"
                  label="Embargo end date (YYYY-MM-DD)"
                  placeholder="2027-12-31"
                  density="compact"
                  hide-details
                  style="max-width: 220px"
                  data-testid="embargo-date-input"
                />
                <v-btn
                  variant="flat"
                  color="primary"
                  size="small"
                  :loading="embargoSaving"
                  data-testid="embargo-save-btn"
                  @click="saveEmbargoEdit"
                >Save</v-btn>
                <v-btn
                  variant="text"
                  size="small"
                  data-testid="embargo-cancel-btn"
                  @click="cancelEmbargoEdit"
                >Cancel</v-btn>
              </span>
              <!-- FAIR3: "Set embargo" affordance when EMBARGOED but no date yet -->
              <v-btn
                v-if="!dataObjectEmbargoEndDate && !embargoEditActive && dataObjectAccessRights === 'EMBARGOED' && isAllowedToEditCollection"
                variant="tonal"
                color="warning"
                size="small"
                prepend-icon="mdi-lock-clock"
                data-testid="embargo-set-btn"
                @click="startEmbargoEdit"
              >Set embargo date</v-btn>
              <!-- FAIR2: createdByOrcid — read-only ORCID badge -->
              <a
                v-if="dataObjectCreatedByOrcid"
                :href="`https://orcid.org/${dataObjectCreatedByOrcid}`"
                target="_blank"
                rel="noopener"
                class="do-orcid-badge"
                :title="`Creator ORCID: ${dataObjectCreatedByOrcid} — click to view on orcid.org`"
                data-testid="created-by-orcid-badge"
              >
                <svg viewBox="0 0 256 256" width="20" height="20" aria-label="ORCID iD" role="img">
                  <circle cx="128" cy="128" r="128" fill="#A6CE39" />
                  <g fill="#FFFFFF">
                    <rect x="83" y="105" width="14" height="78" />
                    <circle cx="90" cy="88" r="9" />
                    <path d="M115 105 h35 c25 0 41 18 41 39 0 22 -18 39 -41 39 h-35 z M129 117 v54 h19 c20 0 28 -14 28 -27 0 -16 -10 -27 -28 -27 z" />
                  </g>
                </svg>
                <span class="do-orcid-id">{{ dataObjectCreatedByOrcid }}</span>
              </a>
            </v-row>
            <v-row
              v-if="dataObject.appId"
              no-gutters
              class="justify-end pb-2 ga-2 align-center"
            >
              <!-- TOOLS-CONTEXT-DO-* — in-context Tools menu grouping the
                   SPARQL query + vocabulary browser + SHACL validate +
                   view-recipe render entry points. Visible to readers
                   too; only the actions themselves require permissions
                   (handled by destination pages). -->
              <EntityToolsMenu
                :app-id="dataObject.appId"
                scope="data-object"
                :attached-template-app-id="(dataObject as unknown as { attachedTemplateAppId?: string | null }).attachedTemplateAppId ?? null"
              />
              <PublishButton
                v-if="isAllowedToEditCollection"
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

            <!-- Always-visible: Semantic Annotation chips.
                 SEMA-V6-017: single "Annotate" button visible in basic AND
                 advanced mode (superset rule). Advanced mode adds the legacy
                 "Add raw attribute" button with a deprecation tooltip. -->
            <section class="page-section">
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">
                  Annotations
                  <!-- UX Pattern D: low-emphasis count badge -->
                  <span
                    v-if="numberOfSemanticAnnotations !== undefined"
                    class="text-low-emphasis ml-1"
                    data-testid="semantic-annotations-count"
                  >({{ numberOfSemanticAnnotations }})</span>
                </div>
                <!-- SEMA-V6-017: primary "Annotate" button — always visible -->
                <v-btn
                  v-if="isAllowedToEditCollection"
                  color="primary"
                  density="comfortable"
                  prepend-icon="mdi-tag-plus-outline"
                  size="small"
                  variant="tonal"
                  data-testid="annotate-button"
                  @click="showAnnotationDialog = true"
                >
                  Annotate
                </v-btn>
                <!-- SEMA-V6-017: legacy "Add raw attribute" — advanced mode only,
                     with deprecation tooltip pointing at the §11 Phase 2/3 migration. -->
                <v-tooltip
                  v-if="advancedMode && isAllowedToEditCollection"
                  text="Legacy path — prefer Annotate for new annotations. Will be removed in §11 Phase 3 (SEMA-V6-012)."
                  location="top"
                  max-width="320"
                >
                  <template #activator="{ props: tip }">
                    <ExpansionPanelTitleButton
                      v-bind="tip"
                      icon="mdi-plus-circle"
                      text="Add/Edit attributes (legacy)"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                  </template>
                </v-tooltip>
              </div>

              <!-- New AnnotationDialog (SEMA-V6-005).
                   Prefers the SEMA-V6-004 polymorphic path when dataObject.appId
                   is available (DataObjects always have appId). Falls back to the
                   Annotated interface for legacy contexts. -->
              <AnnotationDialog
                v-if="showAnnotationDialog && isAllowedToEditCollection"
                v-model:show-dialog="showAnnotationDialog"
                :subject-app-id="dataObject?.appId ?? undefined"
                subject-kind="DataObject"
                :annotated="new AnnotatedDataObject(collectionId, dataObjectId)"
                @annotation-created="handleAnnotationListUpdate"
              />

              <SemanticAnnotationList
                :annotated="
                  new AnnotatedDataObject(collection.id, dataObject.id)
                "
                :can-delete="!!isAllowedToEditCollection"
                @annotations="onAnnotationsLoaded"
              />
            </section>

            <v-row no-gutters>
              <ExpansionPanels :default-open="[1, 2, 3, 4]">
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
                <!-- REF-UNIFIED-TABLE: single unified references table covering
                     all reference kinds (TimeSeries, File Bundle, Structured Data,
                     Git, Video, HDF5). Filter chips at the top let users narrow
                     to a specific kind. New kinds show in the table via extraItems.
                     The old per-kind panes (GitReferencesPane, VideoStreamReferencesPane,
                     HdfReferencesPane) are preserved in components/context/dataobject/legacy/
                     but no longer mounted here. -->
                <ExpansionPanelItem
                  :count="totalReferenceCount"
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
                    :extra-items="extraReferenceItems"
                    :data-object-app-id="dataObject.appId ?? undefined"
                    @refresh="refreshExtraReferences"
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
                    :predecessor-relationship-types="predecessorRelationshipTypesMap"
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
                <!-- J1c retirement (2026-05-29): the dedicated Jupyter Notebooks
                     panel has been merged into the unified Data References
                     table. Notebooks render as rows with a notebook icon and,
                     when the admin-configurable JupyterConfig (J1e) is open,
                     an "Open in JupyterHub" action button. -->
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
                <!-- UX-PROV1: Ancestor chain — advanced mode only.
                     Shows the upstream predecessor chain as a vertical
                     timeline using the ANC-1 predecessor-chain endpoint.
                     Strict superset rule: this panel is additive; it
                     never hides anything shown in basic mode. -->
                <ExpansionPanelItem
                  v-if="advancedMode && dataObject.appId && collection.appId"
                  title="Ancestor Chain"
                >
                  <AncestorChainPanel
                    :collection-id="collectionId"
                    :collection-app-id="collection.appId"
                    :data-object-app-id="dataObject.appId"
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

/* FAIR2: creator ORCID badge on the DataObject detail page.
   Same visual treatment as .orcid-badge in ProfilePane.vue. */
.do-orcid-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  text-decoration: none;
  border-radius: 4px;
  padding: 2px 6px 2px 2px;
  background: rgba(166, 206, 57, 0.12);
  border: 1px solid rgba(166, 206, 57, 0.4);
  cursor: pointer;
  color: inherit;
  font-size: 0.75rem;
}
.do-orcid-badge:hover {
  background: rgba(166, 206, 57, 0.2);
  border-color: #A6CE39;
}
.do-orcid-id {
  font-family: monospace;
  letter-spacing: 0.02em;
  color: #4a7a1a;
}
</style>
