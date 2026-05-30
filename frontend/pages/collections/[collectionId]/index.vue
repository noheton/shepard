<script lang="ts" setup>
import { CollectionApi } from "@dlr-shepard/backend-client";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import PublicationStatusBadge from "~/components/context/publish/PublicationStatusBadge.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { collectionsPath } from "~/utils/constants";
import { useWatchedCollections } from "~/composables/context/useWatchedCollections";
import { useCollectionWatch } from "~/composables/context/useCollectionWatch";
import { useInstanceCapabilities } from "~/composables/context/useInstanceCapabilities";
import { useMffdNdtGridProbe } from "~/composables/context/useMffdNdtGridProbe";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();

const collectionId = routeParams.value.collectionId;
const { collection, isAllowedToEditCollection, isLoading: isCollectionLoading, isError: isCollectionError } =
  useFetchCollection(collectionId);
const { isWatched, toggle: toggleWatched } = useWatchedCollections();
const { dataObjectsMap, fetchMap: fetchDataObjectMap } = useFetchDataObjectMapByCollection(collectionId);
const collectionApi = useShepardApi(CollectionApi);

const showAttributeEditDialog = ref(false);
const showCreateDataObjectDialog = ref(false);
const isExporting = ref(false);

// ── Inline description editing ────────────────────────────────────────────
const descEditActive = ref(false);
const descDraft = ref("");
const descStatusDraft = ref<string | null>(null);
const descSaving = ref(false);

function startDescEdit() {
  descDraft.value = collection.value?.description ?? "";
  descStatusDraft.value = collection.value?.status ?? null;
  descEditActive.value = true;
}

function cancelDescEdit() {
  descEditActive.value = false;
}

async function saveDescEdit() {
  if (!collection.value) return;
  descSaving.value = true;
  try {
    await collectionApi.value.updateCollection({
      collectionId,
      collection: {
        name: collection.value.name,
        description: descDraft.value,
        status: descStatusDraft.value ?? undefined,
        attributes: collection.value.attributes ?? {},
      },
    });
    emitSuccess(`Description updated`);
    handleCollectionUpdate();
    descEditActive.value = false;
  } catch (e) {
    handleError(e, "updating description");
  } finally {
    descSaving.value = false;
  }
}

async function downloadRoCrate() {
  if (isExporting.value) return;
  isExporting.value = true;
  try {
    const blob = await useShepardApi(CollectionApi).value.exportCollection({
      collectionId,
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${collection.value?.name ?? collectionId}-export.zip`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  } catch (e) {
    handleError(e, "downloading RO-Crate export");
  } finally {
    isExporting.value = false;
  }
}

// TPL14 — Regulatory Evidence Pack (REP) export.
// Calls POST /v2/collections/{appId}/export/regulatory-evidence,
// decodes the inline Base64 bag, and triggers a browser download.
// Auth: uses the same Bearer-token pattern as all other /v2/ callers
// (see useStructuredDataContainerLinkedDataObjects, useFetchPayloadVersions, …).
const isRepExporting = ref(false);

function repV2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

async function downloadRepExport() {
  if (isRepExporting.value || !collectionAppId.value) return;
  isRepExporting.value = true;
  try {
    const { data: session } = useAuth();
    const accessToken = session.value?.accessToken;
    if (!accessToken) {
      handleError(new Error("Not authenticated"), "downloading Regulatory Evidence Pack");
      return;
    }
    const url = `${repV2BaseUrl()}/v2/collections/${collectionAppId.value}/export/regulatory-evidence`;
    const httpResponse = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
      },
    });
    if (!httpResponse.ok) {
      handleError(new Error(`HTTP ${httpResponse.status}`), "downloading Regulatory Evidence Pack");
      return;
    }
    const response = (await httpResponse.json()) as {
      bagBase64?: string;
      fileName?: string;
      downloadUrl?: string;
      status?: string;
    };
    const fileName = response.fileName ?? `${collectionAppId.value}-rep.bag.zip`;
    if (response.bagBase64) {
      const binary = atob(response.bagBase64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      const blob = new Blob([bytes], { type: "application/zip" });
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = blobUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);
    } else if (response.downloadUrl) {
      window.open(response.downloadUrl, "_blank");
    }
  } catch (e) {
    handleError(e, "downloading Regulatory Evidence Pack");
  } finally {
    isRepExporting.value = false;
  }
}

// PROV1d: the activity sparkline dashboard is keyed by Collection appId
// (the L2 native identifier; backend `/v2/provenance/stats` rejects the
// legacy numeric id). The generated `Collection` model doesn't yet
// expose `appId` directly, but the wire shape does carry it; read
// defensively so older backend builds don't crash the page.
const collectionAppId = computed<string | null>(() => {
  if (!collection.value) return null;
  const raw = (collection.value as unknown as { appId?: string | null }).appId;
  return raw ?? null;
});

// LIC1 (FAIR-1): same defensive-read pattern as collectionAppId. The
// generated `Collection` client model may not yet expose these fields, but
// the wire payload carries them once the backend ships them.
const collectionLicense = computed<string | null>(() => {
  if (!collection.value) return null;
  const raw = (collection.value as unknown as { license?: string | null }).license;
  return raw ?? null;
});
const collectionAccessRights = computed<string | null>(() => {
  if (!collection.value) return null;
  const raw = (collection.value as unknown as { accessRights?: string | null })
    .accessRights;
  return raw ?? null;
});

// MFFD-NDT-GRID-1 — cheap probe to decide whether the 14x14 thermography
// coverage card should mount on this Collection page. Fetches the DO list
// (cached) + samples the first 5 DOs' annotations for the
// `urn:shepard:mffd:section` predicate. The widget renders only when the
// probe flips `hasData` to true, so collections without OTvis data
// pay only the probe cost (1 list + 5 small annotation fetches).
const mffdNdtCollectionIdRef = computed<number | null>(() => collectionId);
const { hasData: mffdNdtHasData } = useMffdNdtGridProbe(mffdNdtCollectionIdRef);

// Gate the Publishing panel on whether the Unhide plugin is active on
// this instance (INST2 — GET /v2/instance/capabilities, fetched in
// HeaderBar on login). If the plugin is disabled or not installed the
// panel is hidden so users don't see a dead "Publish to Helmholtz KG"
// toggle. The fetch is a singleton so this computed just reads the
// already-resolved state.
const { isPluginEnabled } = useInstanceCapabilities();
const isUnhideEnabled = computed(() => isPluginEnabled("unhide"));

// UX Pattern F (2026-05-24): call useHead once at top-level with a reactive
// title getter. Calling useHead INSIDE a watch creates a fresh head entry on
// every fetch + leaves the initial paint with a stale/generic title until the
// first watch fires. The function form below makes the title reactive.
useHead({
  title: () =>
    collection.value?.name
      ? `${collection.value.name} — shepard`
      : "Collection — shepard",
});
</script>

<template>
  <PageShell>
    <v-container class="pa-0 fill-height" fluid>
      <v-row v-if="!!collection" no-gutters>
        <!-- Feature B: Hero banner — only rendered when heroImageUrl is set.
             #metadata-heroimage-edit doubles as the RDM-005 deep-link target;
             when no hero image is set we render a thin placeholder
             carrying the anchor so the completeness-widget jump still
             lands on something user-visible. -->
        <v-col v-if="collection.heroImageUrl" cols="12" class="pa-0">
          <v-img
            id="metadata-heroimage-edit"
            :src="collection.heroImageUrl"
            height="220"
            cover
            class="collection-hero-banner"
            :alt="collection.name ? `Hero image for ${collection.name}` : 'Collection hero image'"
          >
            <template #error>
              <!-- Graceful 404 handling: simply show nothing when the image fails. -->
            </template>
          </v-img>
        </v-col>
        <v-col v-else cols="12" class="pa-0">
          <div
            id="metadata-heroimage-edit"
            class="hero-image-placeholder text-caption text-medium-emphasis"
          >
            No hero image set — add one via the Edit Collection dialog.
          </div>
        </v-col>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Collections',
                to: collectionsPath,
              },
              {
                title: `${collection.name}`,
                to: collectionsPath + routeParams.collectionId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="collection"
                id-label="Collection ID"
              />
            </v-row>
            <!-- LIC1: FAIR metadata strip — license + accessRights. Shown only
                 when at least one is set; null means undeclared and we
                 deliberately don't show "—" here (the edit dialog is the
                 affordance to set them).
                 #metadata-license-edit doubles as the RDM-005 deep-link
                 target — the completeness widget scrolls here on the
                 "Add license" / "Set access rights" actions. -->
            <v-row
              v-if="collectionLicense || collectionAccessRights || collectionAppId"
              id="metadata-license-edit"
              no-gutters
              class="pb-3 ga-2 align-center"
            >
              <LicenseChip
                v-if="collectionLicense"
                :license="collectionLicense"
              />
              <AccessRightsChip
                v-if="collectionAccessRights"
                :access-rights="collectionAccessRights"
              />
              <!-- KIP1k: publication-status badge — shows "Published" chip when
                   a :Publication row exists for this Collection. Informational;
                   the PublishButton below is the action affordance. -->
              <PublicationStatusBadge
                v-if="collectionAppId"
                entity-kind="collections"
                :entity-app-id="collectionAppId"
              />
            </v-row>
            <!-- RDM-005 deep-link anchor — rendered only when collectionAppId is null
                 (very rare: fallback for pre-L2 collections without an appId).
                 When appId is present the v-row above is always shown
                 (at minimum with the PublicationStatusBadge). -->
            <div
              v-if="!collectionAppId && !collectionLicense && !collectionAccessRights"
              id="metadata-license-edit"
              class="pb-1 text-caption text-medium-emphasis"
            >
              No license or access-rights set — use the Edit dialog above
              to add them.
            </div>
            <v-row
              no-gutters
              class="justify-end pb-2 ga-2 align-center"
            >
              <!-- CW1: Bell button — subscribe to notifications for new DataObjects. -->
              <CollectionWatchButton
                v-if="collectionAppId"
                :collection-app-id="collectionAppId"
              />
              <v-btn
                icon
                variant="text"
                :color="isWatched(collection.id!) ? 'primary' : undefined"
                :title="isWatched(collection.id!) ? 'Remove from watched' : 'Add to watched'"
                @click="toggleWatched(collection)"
              >
                <v-icon>{{ isWatched(collection.id!) ? 'mdi-binoculars' : 'mdi-binoculars-outline' }}</v-icon>
              </v-btn>
              <v-btn
                prepend-icon="mdi-package-down"
                variant="tonal"
                color="secondary"
                :loading="isExporting"
                @click="downloadRoCrate"
              >
                Download as RO-Crate
              </v-btn>
              <!-- TPL14: Regulatory Evidence Pack (REP) export.
                   Builds a BagIt bag with RO-Crate + PROV-O for regulatory
                   submissions (EN 9100, EASA, Clean Aviation). -->
              <v-btn
                v-if="collectionAppId"
                prepend-icon="mdi-certificate-outline"
                variant="tonal"
                color="warning"
                :loading="isRepExporting"
                :title="'Download BagIt Regulatory Evidence Pack (RO-Crate + PROV-O)'"
                @click="downloadRepExport"
              >
                Regulatory Evidence Pack
              </v-btn>
              <PublishButton
                v-if="collectionAppId && isAllowedToEditCollection"
                entity-kind="collections"
                :entity-app-id="collectionAppId"
                :entity-name="collection.name"
              />
            </v-row>
            <!-- Always-visible: Description with inline edit. Lives outside the
                 collapsibles so users don't have to click to read it. -->
            <section
              id="metadata-description-section"
              class="page-section"
            >
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
              <DescriptionDisplay v-else :entity="collection" />
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

            <!-- RDM-001: "Cite this dataset" card. Renders an APA / BibTeX /
                 RIS / CSL JSON citation built from fields already on the
                 Collection wire shape (name, createdBy, createdAt year,
                 license post-LIC1, canonical URL). Highest-visibility spot
                 for the funder-reviewing-the-dataset persona. -->
            <CiteThisCard :collection="collection" />

            <!-- DMP-DOWNLOAD-NAV-01 (FAIR7 UI): "Download DMP" button that
                 fetches the Markdown DMP block from
                 GET /v2/collections/{appId}/dmp-snippet. Sits next to the
                 Cite-this card because the two cover the same funder-facing
                 corner (citation for papers, DMP for funding forms).
                 Rendered only when collectionAppId is resolvable — the v2
                 endpoint is appId-keyed. -->
            <div v-if="collectionAppId" class="dmp-download-row mb-6">
              <DownloadDmpButton
                :collection-app-id="collectionAppId"
                :collection-name="collection.name"
              />
            </div>

            <!-- RDM-005: Metadata completeness score widget. 0–100 score
                 with red/amber/green chip + per-check breakdown driving
                 operators to fill the FAIR R1.1 / R1.3 gaps the prior
                 13 UI improvements left untouched. Pure frontend — feeds
                 off the wire shape + three cheap fetches (annotations,
                 lab journal, creator ORCID). -->
            <MetadataCompletenessCard :collection="collection" />

            <!-- MFFD-NDT-GRID-1: 14x14 OTvis thermography coverage tracker.
                 Renders only when the cheap probe in useMffdNdtGridProbe
                 finds at least one DataObject carrying
                 urn:shepard:mffd:section -- collections without
                 thermography data don't pay the rendering cost. The
                 widget's own fetch (full per-DO annotation pull) only
                 fires once it's mounted. -->
            <MffdNdtGridCard
              v-if="mffdNdtHasData === true"
              :collection-id="collectionId"
            />

            <!-- Always-visible: Semantic Annotation chips. Out of the
                 collapsibles so users see the tags at a glance. -->
            <section
              id="metadata-annotations-section"
              class="page-section"
            >
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Semantic Annotations</div>
                <AddAnnotationButton
                  v-if="isAllowedToEditCollection"
                  :annotated="new AnnotatedCollection(collectionId)"
                />
              </div>
              <SemanticAnnotationList
                :annotated="new AnnotatedCollection(collection.id)"
                :can-delete="!!isAllowedToEditCollection"
              />
            </section>

            <!-- Always-visible: flat, searchable DataObjects list. The
                 #24 "Collection-scale navigation" entry point — user
                 can answer "where is X" without opening collapsibles. -->
            <section
              id="metadata-dataobjects-section"
              class="page-section"
            >
              <div class="page-section-head">
                <div class="text-h5 text-textbody1">Data Objects</div>
                <v-btn
                  v-if="isAllowedToEditCollection"
                  variant="tonal"
                  size="small"
                  color="primary"
                  prepend-icon="mdi-plus-circle-outline"
                  @click="showCreateDataObjectDialog = true"
                >
                  New DataObject
                </v-btn>
              </div>
              <CollectionDataObjectsPanel :collection-id="collectionId" :collection-app-id="collectionAppId" />
              <!-- Re-uses the existing CreateDataObjectDialog which already
                   includes the template picker when allowed templates exist
                   for the Collection — passing `collectionAppId` flips it
                   into picker-first mode (was sidebar-only before). -->
              <CreateDataObjectDialog
                v-if="showCreateDataObjectDialog && collectionAppId"
                v-model:show-dialog="showCreateDataObjectDialog"
                :collection-id="collectionId"
                :collection-app-id="collectionAppId"
              />
            </section>

            <!-- Deeper-dive content stays in collapsibles. Snapshots and
                 Publishing are dev/admin tooling — hidden in basic mode. -->
            <v-row no-gutters>
              <ExpansionPanels :default-open="[0, 1, 3]">
                <ExpansionPanelItem
                  :count="Object.keys(collection.attributes ?? {}).length"
                  title="Attributes"
                >
                  <AttributesDisplay :entity="collection" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-plus-circle"
                      text="Add/Edit"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                    <EditCollectionAttributesDialog
                      v-if="showAttributeEditDialog"
                      v-model:show-dialog="showAttributeEditDialog"
                      :collection="collection"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  :count="numberOfLabJournalEntries"
                  title="Lab Journal"
                >
                  <div id="metadata-labjournal-section" class="pt-4">
                    <CollectionLabJournalEntryList
                      :collection-id="routeParams.collectionId"
                      :collection-app-id="collectionAppId"
                      :data-object-map="dataObjectsMap"
                      :fetch-data-object-map="fetchDataObjectMap"
                      @number-of-entries-changed="onLabJournalCountChanged"
                    />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="collectionAppId"
                  title="Activity"
                >
                  <div class="pt-4">
                    <ActivitySparklineCard
                      :collection-app-id="collectionAppId"
                    />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  title="Referenced Containers"
                  :count="undefined"
                >
                  <div class="pt-2 pb-2">
                    <CollectionContainersPanel :collection-app-id="collectionAppId" />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem title="Dataset Lineage">
                  <div class="pt-2 pb-2">
                    <CollectionLineageGraph :collection-id="collectionId" />
                  </div>
                </ExpansionPanelItem>
                <!-- WATCH1 — containers this collection is watching but does
                     not own via DataObject references. Useful for live-data
                     collections (home-showcase) that don't structure their
                     data per-DataObject. -->
                <ExpansionPanelItem
                  v-if="collectionAppId"
                  title="Watched containers"
                >
                  <div class="pt-2 pb-2">
                    <WatchedContainersPanel
                      :collection-app-id="collectionAppId"
                      :is-allowed-to-edit="!!isAllowedToEditCollection"
                    />
                  </div>
                </ExpansionPanelItem>
                <!-- Snapshots and Publishing are panels of data, not advanced-mode
                     fields — visible to anyone with edit permission, in both modes
                     (per the refined basic/advanced policy: the toggle gates fields,
                     not whole panels). -->
                <ExpansionPanelItem
                  v-if="isAllowedToEditCollection && collectionAppId"
                  title="Snapshots"
                >
                  <div class="pt-4">
                    <SnapshotsPane :collection-app-id="collectionAppId" />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="isAllowedToEditCollection && collectionAppId && isUnhideEnabled"
                  title="Publishing"
                >
                  <div class="pt-2">
                    <CollectionPropertiesPane :collection-app-id="collectionAppId" />
                  </div>
                </ExpansionPanelItem>
              </ExpansionPanels>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <NotFoundPanel v-else-if="isCollectionError" />
      <CenteredLoadingSpinner v-else />
    </v-container>
  </PageShell>
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
}
.collection-hero-banner {
  border-radius: 4px;
  margin-bottom: 8px;
}
.hero-image-placeholder {
  padding: 6px 4px;
}
</style>
