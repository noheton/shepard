<script setup lang="ts">
import { collectionsPath } from "../../../utils/constants";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();

const {
  counter: numberOfLabJournalEntries,
  updateCount: onLabJournalCountChanged,
} = useCounter();

const { collection } = useFetchCollection(routeParams.value.collectionId);
const { dataObjectsMap } = useFetchDataObjectMapByCollection(
  routeParams.value.collectionId,
);

const showAttributeEditDialog = ref(false);
const showDescriptionEditDialog = ref(false);
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height">
      <v-row v-if="!!collection" no-gutters>
        <v-col cols="12">
          <LayoutComponentsShepardBreadcrumbs
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
              <EntityTitle :entity="collection" id-label="Collection ID" />
            </v-row>
            <v-row no-gutters>
              <EntityExpansionPanels>
                <EntityExpansionPanelItem title="Description">
                  <EntityDescription :entity="collection" />
                  <template #append>
                    <EntityExpansionPanelTitleButton
                      text="Edit"
                      icon="mdi-pencil-outline"
                      @click="() => (showDescriptionEditDialog = true)"
                    />
                    <CollectionEditDialog
                      v-model:show-dialog="showDescriptionEditDialog"
                      :collection="collection"
                      title="Edit Description"
                    >
                      <template
                        #inputs="{ updatedCollection, updateCollection }"
                      >
                        <v-row class="pt-8" />
                        <CommonInputDescription
                          :description="updatedCollection.description"
                          @description-changed="
                            description =>
                              updateCollection({
                                ...updatedCollection,
                                description,
                              })
                          "
                        />
                      </template>
                    </CollectionEditDialog>
                  </template>
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
                  title="Attributes"
                  :count="Object.keys(collection.attributes ?? {}).length"
                >
                  <EntityAttributes :entity="collection" />
                  <template #append>
                    <EntityExpansionPanelTitleButton
                      text="Add/Edit"
                      icon="mdi-plus-circle"
                      @click="() => (showAttributeEditDialog = true)"
                    />
                    <CollectionEditDialog
                      v-model:show-dialog="showAttributeEditDialog"
                      :collection="collection"
                      title="Add / Edit Attributes"
                    >
                      <template
                        #inputs="{ updatedCollection, updateCollection }"
                      >
                        <v-row class="pt-8" />
                        <CommonInputAttributes
                          :attributes="updatedCollection.attributes"
                          @attributes-changed="
                            attributes =>
                              updateCollection({
                                ...updatedCollection,
                                attributes,
                              })
                          "
                        />
                      </template>
                    </CollectionEditDialog>
                  </template>
                </EntityExpansionPanelItem>
                <EntityExpansionPanelItem
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
