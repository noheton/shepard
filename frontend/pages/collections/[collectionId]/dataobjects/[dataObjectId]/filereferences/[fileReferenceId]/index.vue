<script lang="ts" setup>
import { FileReferenceApi } from "@dlr-shepard/backend-client";
import ActionButton from "~/components/common/data-table/ActionButton.vue";
import type {
  FileType,
  ShepardFileDataTableItem,
} from "~/components/context/display-components/file-references/ShepardFileDataTableItem";
import { mapShepardFilesToDataTableItems } from "~/components/context/display-components/file-references/shepardFileMappingUtil";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useFetchFileReference } from "~/composables/context/useFetchFileReference";
import { resolveNumericId } from "~/utils/collectionRouteParams";

definePageMeta({ layout: "collection" });

const { routeParams } = useCollectionRouteParams();
const collectionIdStr = routeParams.value.collectionId ?? "";
const dataObjectIdStr = routeParams.value.dataObjectId ?? "";

const showDeleteDialog = ref<boolean>(false);
const showAddAnnotationDialog = ref<boolean>(false);
const showFileContentViewerDialog = ref<boolean>(false);
const showEditDialog = ref<boolean>(false);
const fileDataTableItems = ref<ShepardFileDataTableItem[]>([]);
const selectedOid = ref<string>("");
const selectedFileType = ref<FileType>("unknown");
const selectedFileName = ref<string>("");

const { collection, isAllowedToEditCollection } =
  useFetchCollection(collectionIdStr);
const { dataObject } = useFetchDataObject(collectionIdStr, dataObjectIdStr);

// BUG-COLL-APPID-ROUTE-007-REFPAGE: resolve numeric ids from the loaded v2
// entities; defer all v1 calls until both are available. UUID route params
// must never be cast directly to numbers for v1 endpoints.
const collectionNumericId = computed(() =>
  resolveNumericId(collection.value?.id, routeParams.value.collectionId),
);
const dataObjectNumericId = computed(() =>
  resolveNumericId(dataObject.value?.id, routeParams.value.dataObjectId),
);
const fileReferenceNumericId = computed(() =>
  resolveNumericId(undefined, routeParams.value.fileReferenceId),
);

const { fileReference, files } = useFetchFileReference(
  collectionNumericId,
  dataObjectNumericId,
  fileReferenceNumericId,
);

const headers = ref([
  { title: "Name", key: "name", sortable: true },
  { title: "Oid", key: "oid", sortable: true },
  { title: "Created at", key: "createdAt", sortable: true },
  { title: "", key: "actions" },
]);

const itemsPerPage = 10;

watch(files, () => {
  fileDataTableItems.value = mapShepardFilesToDataTableItems(files.value);
});

function onAnnotate() {
  showAddAnnotationDialog.value = true;
}

function onEdit() {
  showEditDialog.value = true;
}

function onDelete() {
  showDeleteDialog.value = true;
}

function deleteFileReference() {
  const c = collectionNumericId.value;
  const d = dataObjectNumericId.value;
  if (fileReference.value && c && d) {
    useShepardApi(FileReferenceApi)
      .value.deleteFileReference({
        collectionId: c,
        dataObjectId: d,
        fileReferenceId: fileReference.value.id,
      })
      .then(() => {
        navigateTo(
          collectionsPath +
            routeParams.value.collectionId +
            dataObjectsPathFragment +
            routeParams.value.dataObjectId,
        );
      })
      .catch(error => {
        handleError(error, "deleteFileReference");
        showDeleteDialog.value = false;
      });
  }
}

function onShowFileContentDialog(params: {
  oid: string;
  fileType: FileType;
  fileName: string;
}) {
  selectedOid.value = params.oid;
  selectedFileType.value = params.fileType;
  selectedFileName.value = params.fileName;
  showFileContentViewerDialog.value = true;
}

const fileReferenceDisplayName = computed(() =>
  fileReference.value ? `File Reference "${fileReference.value.name}"` : '',
);

function onDownloadFile(params: { filename: string; oid: string }) {
  const c = collectionNumericId.value;
  const d = dataObjectNumericId.value;
  const r = fileReferenceNumericId.value;
  if (!c || !d || !r) return;
  const filename = sanitizeFilename(params.filename);

  useShepardApi(FileReferenceApi)
    .value.getFilePayload({
      collectionId: c,
      dataObjectId: d,
      fileReferenceId: r,
      oid: params.oid,
    })
    .then(response => {
      downloadFile(response, filename);
    })
    .catch(e => {
      handleError(e, "downloading file");
    });
}

watch(fileReference, () => {
  useHead({
    title: fileReference.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1000px">
    <v-container class="pa-0 fill-height" fluid max-width="1000px">
      <v-row v-if="!!collection && !!dataObject && !!fileReference">
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
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId,
              },
              {
                title: `${fileReference?.name}`,
                to:
                  collectionsPath +
                  routeParams.collectionId +
                  dataObjectsPathFragment +
                  routeParams.dataObjectId +
                  fileReferencesPathFragment +
                  routeParams.fileReferenceId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <TitleAndMetadataDisplay
                :entity="{
                  ...fileReference,
                  name: fileReferenceDisplayName,
                  type: 'File',
                  container: {
                    title:
                      fileReference.referencedContainerName ?? 'unknown name',
                    id: fileReference.fileContainerId,
                    type: 'FILE',
                    availability: fileReference.referencedContainerAvailability,
                  },
                }"
                :on-annotate="onAnnotate"
                :on-delete="onDelete"
                :on-edit="fileReference.appId ? onEdit : undefined"
                id-label="ID"
              />
            </v-row>
            <v-row v-if="fileReference?.appId && dataObject?.appId">
              <v-col cols="12" class="d-flex flex-wrap ga-2">
                <InterpretAsTrajectoryButton
                  :file-reference="fileReference"
                  :collection-id="collectionNumericId ?? 0"
                  :data-object-id="dataObjectNumericId ?? 0"
                  :data-object-app-id="dataObject.appId"
                  :data-object-path="collectionsPath + routeParams.collectionId + dataObjectsPathFragment + routeParams.dataObjectId"
                  :can-edit="!!isAllowedToEditCollection"
                />
                <OpenIn3dViewButton
                  :file-reference-name="fileReference.name"
                  :file-reference-app-id="fileReference.appId"
                />
              </v-col>
            </v-row>
            <v-row align="center" justify="space-between">
              <v-col>
                <SemanticAnnotationList
                  v-if="fileReference?.appId"
                  :annotated="
                    new AnnotatedReference(fileReference.appId, 'FileReference')
                  "
                  :can-delete="!!isAllowedToEditCollection"
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
                :items-for-pagination="fileDataTableItems"
              >
                <template
                  #[`item.name`]="{
                    value,
                    item,
                  }: {
                    value: ShepardFileDataTableItem['name'];
                    item: ShepardFileDataTableItem;
                  }"
                >
                  <a
                    v-if="
                      item.actions.showDetails.enabled &&
                      item.actions.showDetails.fileType !== 'unknown'
                    "
                    href="#"
                    class="file-name-link"
                    @click.prevent="() => onShowFileContentDialog(item.actions.showDetails)"
                  >{{ value.filename }}</a>
                  <span v-else>{{ value.filename }}</span>
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
                    value: ShepardFileDataTableItem['actions'];
                  }"
                >
                  <ActionContainer>
                    <ActionButton
                      v-if="value.download.enabled"
                      icon="mdi-tray-arrow-down"
                      aria-label="Download file"
                      @click="() => onDownloadFile(value.download)"
                    />
                    <ActionButton
                      v-if="
                        value.showDetails.enabled &&
                        value.showDetails.fileType !== 'unknown'
                      "
                      icon="mdi-eye-outline"
                      aria-label="View file"
                      @click="() => onShowFileContentDialog(value.showDetails)"
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
    <EditFileReferenceDialog
      v-if="showEditDialog && fileReference?.appId"
      v-model:show-dialog="showEditDialog"
      :file-reference-app-id="fileReference.appId"
      :current-name="fileReference.name"
      @saved="(newName) => { if (fileReference) fileReference.name = newName; }"
    />
    <ConfirmDeleteDialog
      v-if="showDeleteDialog"
      v-model:show-dialog="showDeleteDialog"
      @confirmed="deleteFileReference"
    />
    <AddAnnotationDialog
      v-if="showAddAnnotationDialog && fileReference?.appId"
      v-model:show-dialog="showAddAnnotationDialog"
      :annotated="
        new AnnotatedReference(fileReference.appId, 'FileReference')
      "
    />
    <FileContentViewerDialog
      v-if="showFileContentViewerDialog"
      v-model:show-dialog="showFileContentViewerDialog"
      :collection-id="collectionNumericId ?? 0"
      :data-object-id="dataObjectNumericId ?? 0"
      :file-reference-id="fileReferenceNumericId ?? 0"
      :file-type="selectedFileType"
      :file-name="selectedFileName"
      :oid="selectedOid"
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

.file-name-link {
  color: rgb(var(--v-theme-primary));
  text-decoration: none;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}
</style>
