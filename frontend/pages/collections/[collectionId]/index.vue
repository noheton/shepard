<script lang="ts" setup>
import { CollectionApi } from "@dlr-shepard/backend-client";
import PublishButton from "~/components/context/publish/PublishButton.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { collectionsPath } from "~/utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();

const collectionId = routeParams.value.collectionId;
const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionId);
const { dataObjectsMap } = useFetchDataObjectMapByCollection(collectionId);

const showAttributeEditDialog = ref(false);
const showDescriptionEditDialog = ref(false);
const isExporting = ref(false);

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
            <v-row no-gutters>
              <ExpansionPanels>
                <ExpansionPanelItem title="Description">
                  <DescriptionDisplay :entity="collection" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      icon="mdi-pencil-outline"
                      text="Edit"
                      @click="() => (showDescriptionEditDialog = true)"
                    />
                    <EditCollectionDescriptionDialog
                      v-if="showDescriptionEditDialog"
                      v-model:show-dialog="showDescriptionEditDialog"
                      :collection="collection"
                    />
                  </template>
                </ExpansionPanelItem>
                <ExpansionPanelItem title="Semantic Annotations">
                  <template v-if="isAllowedToEditCollection" #append>
                    <AddAnnotationButton
                      :annotated="new AnnotatedCollection(collectionId)"
                    />
                  </template>
                  <SemanticAnnotationList
                    :annotated="new AnnotatedCollection(collection.id)"
                    :can-delete="!!isAllowedToEditCollection"
                  />
                </ExpansionPanelItem>

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
              </ExpansionPanels>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
    </v-container>
  </div>
</template>
