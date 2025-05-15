<script lang="ts" setup>
import { StructuredDataReferenceApi } from "@dlr-shepard/backend-client";
import ActionButton from "~/components/common/data-table/ActionButton.vue";
import type { StructuredDataDataTableItem } from "~/components/context/display-components/structured-data-references/structuredDataDataTableItem";
import { mapStructuredDataListToDataTableItems } from "~/components/context/display-components/structured-data-references/structuredDataMappingUtil";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useFetchStructuredDataReference } from "~/composables/context/useFetchStructuredDataReference";

definePageMeta({ layout: "collection" });

useHead({
  title: "Structured Data Reference  | shepard",
});

const { routeParams } = useCollectionRouteParams();
const { collectionId, dataObjectId, structuredDataReferenceId } =
  routeParams.value as CollectionRouteParams & {
    dataObjectId: number;
    structuredDataReferenceId: number;
  };

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);
const showStructuredDataContentViewerDialog = ref<boolean>(false);
const structuredDataDataTableItems = ref<StructuredDataDataTableItem[]>([]);
const selectedPayload = ref<string>("");

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionId);
const { dataObject } = useFetchDataObject(collectionId, dataObjectId);
const { structuredDataReference, structuredData } =
  useFetchStructuredDataReference(
    collectionId,
    dataObjectId,
    structuredDataReferenceId,
  );

const headers = ref([
  { title: "Name", key: "name", sortable: true },
  { title: "Oid", key: "oid", sortable: true },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", key: "actions" },
]);

watch(structuredData, () => {
  structuredDataDataTableItems.value = mapStructuredDataListToDataTableItems(
    structuredData.value,
  );
});

function onAnnotate() {
  showAddAnnotationDialog.value = true;
}

function onDelete() {
  showDeleteDialog.value = true;
}

function deleteStructuredDataReference() {
  if (structuredDataReference.value) {
    useShepardApi(StructuredDataReferenceApi)
      .value.deleteStructuredDataReference({
        collectionId,
        dataObjectId,
        structuredDataReferenceId: structuredDataReference.value.id,
      })
      .then(() => {
        navigateTo(
          collectionsPath +
            collectionId +
            dataObjectsPathFragment +
            dataObjectId,
        );
      })
      .catch(error => {
        handleError(error, "deleteStructuredDataReference");
        showDeleteDialog.value = false;
      });
  }
}

function onShowStructuredDataContentDialog(structuredDataPayload: string) {
  selectedPayload.value = structuredDataPayload;
  showStructuredDataContentViewerDialog.value = true;
}

const itemsPerPage = 10;
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!structuredDataReference">
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
              {
                title: `Structured Data Reference '${structuredDataReference?.name}'`,
                to:
                  collectionsPath +
                  collectionId +
                  dataObjectsPathFragment +
                  dataObjectId +
                  structuredDataReferencesPathFragment +
                  structuredDataReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...structuredDataReference,
                  name: `Structured Data Reference “${structuredDataReference.name}”`,
                  type: 'Structured Data',
                  container: {
                    title:
                      structuredDataReference.referencedContainerName ??
                      'unknown name',
                    id: structuredDataReference.structuredDataContainerId,
                    type: 'STRUCTUREDDATA',
                    availability:
                      structuredDataReference.referencedContainerAvailability,
                  },
                }"
                :on-annotate="onAnnotate"
                :on-delete="onDelete"
                id-label="ID"
              />
            </v-row>
            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  :can-delete="!!isAllowedToEditCollection"
                  :annotated="
                    new AnnotatedReference(
                      collection.id,
                      dataObjectId,
                      structuredDataReferenceId,
                    )
                  "
                />
              </v-col>
            </v-row>
            <v-row>
              <DataTable
                :items-per-page="itemsPerPage"
                :cell-props="{
                  class: 'text-textbody1',
                }"
                :header-props="{
                  class: 'text-subtitle-2 text-textbody1',
                }"
                :headers="headers"
                :items-for-pagination="structuredDataDataTableItems"
              >
                <template
                  #[`item.name`]="{
                    value,
                  }: {
                    value: StructuredDataDataTableItem['name'];
                  }"
                >
                  {{ value.structuredDataName }}
                  <span
                    v-if="value.availability !== 'available'"
                    class="text-error"
                  >
                    ({{ value.availability }})
                  </span>
                </template>
                <template #[`item.createdAt`]="{ value }: { value: Date }">
                  {{ toShortDateString(value) }}
                </template>
                <template
                  #[`item.actions`]="{
                    value,
                  }: {
                    value: StructuredDataDataTableItem['actions'];
                  }"
                >
                  <ActionContainer>
                    <ActionButton
                      v-if="value.showPayload.enabled"
                      icon="mdi-eye-outline"
                      @click="
                        () =>
                          onShowStructuredDataContentDialog(
                            value.showPayload.payload,
                          )
                      "
                    />
                  </ActionContainer>
                </template>
              </DataTable>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
    </v-container>
    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteStructuredDataReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog"
      v-model:show-dialog="showAddAnnotationDialog"
      :annotated="
        new AnnotatedReference(
          collectionId,
          dataObjectId,
          structuredDataReferenceId,
        )
      "
    />
    <StructuredDataViewerDialog
      v-if="showStructuredDataContentViewerDialog"
      v-model:show-dialog="showStructuredDataContentViewerDialog"
      :structured-data-payload="selectedPayload"
    />
  </div>
</template>

<style lang="scss" scoped>
.v-table {
  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
