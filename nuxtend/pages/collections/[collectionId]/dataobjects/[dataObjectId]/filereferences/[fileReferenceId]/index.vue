<script setup lang="ts">
import { FileReferenceApi } from "@dlr-shepard/backend-client";
import { useFetchFileReference } from "~/composables/context/useFetchFileReference";

definePageMeta({ layout: "collection" });

useHead({
  title: "File Reference  | shepard",
});

const { routeParams } = useCollectionRouteParams();
const { collectionId, dataObjectId, fileReferenceId } =
  routeParams.value as CollectionRouteParams & {
    dataObjectId: number;
    fileReferenceId: number;
  };

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);

const { collection } = useFetchCollection(collectionId);
const { dataObject } = useFetchDataObject(collectionId, dataObjectId);
const { fileReference, files } = useFetchFileReference(
  collectionId,
  dataObjectId,
  fileReferenceId,
);

const headers = ref([
  { title: "Name", key: "filename", sortable: true },
  { title: "Oid", key: "oid", sortable: true },
  { title: "Created at", key: "createdAt", sortable: true },
]);

function onAnnotate() {
  showAddAnnotationDialog.value = true;
}

function onDelete() {
  showDeleteDialog.value = true;
}

function deleteFileReference() {
  if (fileReference.value) {
    createApiInstance(FileReferenceApi)
      .deleteFileReference({
        collectionId,
        dataObjectId,
        fileReferenceId: fileReference.value.id,
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
        handleError(error, "deleteFileReference");
        showDeleteDialog.value = false;
      });
  }
}
</script>

<template>
  <div style="max-width: 1000px">
    <v-container fluid class="pa-0 fill-height" max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!fileReference">
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
                title: `File Reference '${fileReference?.name}'`,
                to:
                  collectionsPath +
                  collectionId +
                  dataObjectsPathFragment +
                  dataObjectId +
                  fileReferencesPathFragment +
                  fileReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container fluid class="pa-0">
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...fileReference,
                  name: `File Reference “${fileReference.name}”`,
                  type: 'File',
                  container: {
                    title: 'unknown name',
                    id: fileReference.fileContainerId,
                    path: containersPath + fileReference.fileContainerId,
                  },
                }"
                id-label="ID"
                :on-delete="onDelete"
                :on-annotate="onAnnotate"
              />
            </v-row>
            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  :annotated="
                    new AnnotatedReference(
                      collection.id,
                      dataObjectId,
                      fileReferenceId,
                    )
                  "
                />
              </v-col>
            </v-row>
            <v-row>
              <DataTable
                :header-props="{
                  class: 'text-subtitle-2 text-textbody1',
                }"
                :cell-props="{
                  class: 'text-textbody1',
                }"
                :headers="headers"
                :items="files"
              >
                <template #[`item.createdAt`]="{ value }: { value: Date }">
                  {{ toShortDateString(value) }}
                </template>
                <template #bottom>
                  <v-divider :thickness="8" color="divider2" opacity="1" />
                  <v-pagination :total-visible="10" />
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
      @confirmed="deleteFileReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog"
      v-model:show-dialog="showAddAnnotationDialog"
      :collection-id="collectionId"
      :data-object-id="dataObjectId"
      :reference-id="fileReferenceId"
    />
  </div>
</template>

<style scoped lang="scss">
.v-table {
  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }

  :deep(.word-wrap-anywhere) {
    word-wrap: anywhere;
  }

  :deep(tbody) > tr > td {
    padding: 20px 24px !important;
  }
}
</style>
