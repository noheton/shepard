<script lang="ts" setup>
import { CollectionApi } from "@dlr-shepard/backend-client";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import { collectionsPath } from "~/utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();
const { advancedMode } = useAdvancedMode();

const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();

const collectionId = routeParams.value.collectionId;
const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionId);
const { dataObjectsMap } = useFetchDataObjectMapByCollection(collectionId);
const collectionApi = useShepardApi(CollectionApi);

const showAttributeEditDialog = ref(false);
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

watch(collection, () => {
  useHead({
    title: collection.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid>
      <v-row v-if="!!collection" no-gutters>
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
            <v-row
              no-gutters
              class="justify-end pb-2 ga-2"
            >
              <v-btn
                prepend-icon="mdi-package-down"
                variant="tonal"
                color="secondary"
                :loading="isExporting"
                @click="downloadRoCrate"
              >
                Download as RO-Crate
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

            <!-- Always-visible: Semantic Annotation chips. Out of the
                 collapsibles so users see the tags at a glance. -->
            <section class="page-section">
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

            <!-- Deeper-dive content stays in collapsibles. Snapshots and
                 Publishing are dev/admin tooling — hidden in basic mode. -->
            <v-row no-gutters>
              <ExpansionPanels>
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
                  <div class="pt-4">
                    <CollectionLabJournalEntryList
                      :collection-id="routeParams.collectionId"
                      :data-object-map="dataObjectsMap"
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
                <ExpansionPanelItem title="Dataset Lineage">
                  <div class="pt-2 pb-2">
                    <CollectionLineageGraph :collection-id="collectionId" />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="advancedMode && isAllowedToEditCollection && collectionAppId"
                  title="Snapshots"
                >
                  <div class="pt-4">
                    <SnapshotsPane :collection-app-id="collectionAppId" />
                  </div>
                </ExpansionPanelItem>
                <ExpansionPanelItem
                  v-if="advancedMode && isAllowedToEditCollection && collectionAppId"
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
  // Match the v-expansion-panel-title left edge so reading flow is consistent
  // with the collapsibles below.
  padding-left: 32px;
}
</style>
