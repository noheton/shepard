<script setup lang="ts">
import { collectionsPath } from "../../../utils/constants";

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
const showAddAnnotationDialog = ref(false);
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height">
      <v-row v-if="!!collection" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
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
              <TitleAndMetadataDisplay
                :entity="collection"
                id-label="Collection ID"
              />
            </v-row>
            <v-row no-gutters>
              <ExpansionPanels>
                <ExpansionPanelItem title="Description">
                  <DescriptionDisplay :entity="collection" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      text="Edit"
                      icon="mdi-pencil-outline"
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
                    <ExpansionPanelTitleButton
                      text="Add"
                      icon="mdi-plus-circle"
                      @click="() => (showAddAnnotationDialog = true)"
                    />
                    <AddAnnotationDialog
                      v-if="showAddAnnotationDialog"
                      v-model:show-dialog="showAddAnnotationDialog"
                      :collection-id="collection.id"
                    />
                  </template>
                  <SemanticAnnotationList
                    :annotated="new AnnotatedCollection(collection.id)"
                  />
                </ExpansionPanelItem>

                <ExpansionPanelItem
                  title="Attributes"
                  :count="Object.keys(collection.attributes ?? {}).length"
                >
                  <AttributesDisplay :entity="collection" />
                  <template v-if="isAllowedToEditCollection" #append>
                    <ExpansionPanelTitleButton
                      text="Add/Edit"
                      icon="mdi-plus-circle"
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
