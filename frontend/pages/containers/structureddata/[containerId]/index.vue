<script setup lang="ts">
import {
  StructuredDataContainerApi,
  type StructuredData,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { StructuredDataContainerAccessor } from "~/composables/container/StructuredDataAccessor";
import { useStructuredDataContainerLinkedDataObjects } from "~/composables/containers/useStructuredDataContainerLinkedDataObjects";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.STRUCTUREDDATA;

const containerAccessor = new StructuredDataContainerAccessor(containerId);
const { dataObjects: linkedDataObjects, isLoading: linkedDataObjectsLoading } =
  useStructuredDataContainerLinkedDataObjects(containerId);

onContainerUpdated(() => {
  fetchData();
});

const fetchData = () => {
  containerAccessor.fetchData();
  containerAccessor.fetchItems();
  containerAccessor.fetchRoles();
};

const itemToDelete = ref<StructuredData | undefined>(undefined);
const showFileDeleteConfirmDialog = ref<boolean>(false);
const showStructuredDataContentViewerDialog = ref<boolean>(false);
const selectedPayload = ref<string>("");

const deleteItem = (item: StructuredData) => {
  itemToDelete.value = item;
  showFileDeleteConfirmDialog.value = true;
};

const uploadFile = async (file: File): Promise<void> => {
  const content = await file.text();
  const json = JSON.parse(content);
  if (json && typeof json === "object") {
    await containerAccessor.uploadItem(file.name, content);
  } else {
    throw new Error("Invalid JSON");
  }
};

const filterFiles = (files: File[]) => {
  return files.filter(file => {
    const fileName = file.name.toLowerCase();
    return fileName.endsWith(".json");
  });
};

function onShowStructuredDataContentDialog(structuredData: StructuredData) {
  if (structuredData.oid) {
    useShepardApi(StructuredDataContainerApi)
      .value.getStructuredData({
        oid: structuredData.oid,
        structuredDataContainerId: containerId,
      })
      .then(response => {
        if (response.payload) {
          selectedPayload.value = response.payload;
          showStructuredDataContentViewerDialog.value = true;
        }
      })
      .catch(error => {
        handleError(error, "fetchStructuredData");
      });
  }
}

function onDownload(structuredData: StructuredData) {
  if (structuredData.oid) {
    useShepardApi(StructuredDataContainerApi)
      .value.getStructuredData({
        oid: structuredData.oid,
        structuredDataContainerId: containerId,
      })
      .then(response => {
        const blob = structuredDataToBlob(response);
        downloadFile(blob, structuredData.name ?? "");
      })
      .catch(error => {
        handleError(error, "downloadStructuredData");
      });
  }
}

fetchData();

watch(containerAccessor.container, () => {
  useHead({
    title: containerAccessor.container.value?.name + " | shepard",
  });
});
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row v-if="!!containerAccessor.container.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: containerAccessor.container.value.name,
                to: containersPath + urlSegment + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <ContainerTitleAndMetadataDisplay
                :id="containerAccessor.container.value.id"
                :n-items="containerAccessor.items.value.length"
                :name="containerAccessor.container.value.name"
                :type-label="'Structured Data Container'"
              >
                <template #buttons>
                  <UploadFilesButton
                    v-if="containerAccessor.isAllowedToEditData.value"
                    accept="application/json"
                    button-text="Upload JSON"
                    dialog-title="Upload JSON"
                    :filter="filterFiles"
                    :upload-file="uploadFile"
                    @upload-finished="fetchData"
                  />
                  <EditPermissionsButton
                    v-if="containerAccessor.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="containerAccessor"
                  />
                  <DeleteContainerButton
                    v-if="containerAccessor.isAllowedToDelete.value"
                    :entity-name="containerAccessor.container.value.name"
                    @delete="containerAccessor.delete()"
                  />
                </template>
              </ContainerTitleAndMetadataDisplay>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
      <StructuredDataTable
        :is-allowed-to-edit="containerAccessor.isAllowedToEditData.value"
        :items="containerAccessor.items.value"
        :loading="containerAccessor.loading.value"
        @delete-item="(item: StructuredData) => deleteItem(item)"
        @show-structured-data-content-dialog="
          (structuredData: StructuredData) =>
            onShowStructuredDataContentDialog(structuredData)
        "
        @download-structured-data="
          (structuredData: StructuredData) => onDownload(structuredData)
        "
      />
      <!-- CC1b: Referenced by — wired to GET /v2/structured-data-containers/{id}/linked-data-objects -->
      <ExpansionPanels class="mt-4">
        <ExpansionPanelItem title="Referenced by">
          <div v-if="linkedDataObjectsLoading" class="pa-4">
            <v-progress-circular indeterminate size="20" />
          </div>
          <div
            v-else-if="!linkedDataObjects || linkedDataObjects.length === 0"
            class="pa-4 text-medium-emphasis text-body-2"
          >
            No linked datasets found.
          </div>
          <div v-else class="pa-2">
            <v-list density="compact">
              <v-list-item
                v-for="obj in linkedDataObjects"
                :key="obj.id"
                :to="`/collections/${obj.collectionId}/dataObjects/${obj.id}`"
                prepend-icon="mdi-database-outline"
                :title="obj.name"
              />
            </v-list>
          </div>
        </ExpansionPanelItem>
      </ExpansionPanels>
    </v-container>
    <ConfirmDeleteDialog
      v-if="
        showFileDeleteConfirmDialog && itemToDelete?.oid && itemToDelete.name
      "
      v-model:show-dialog="showFileDeleteConfirmDialog"
      @confirmed="containerAccessor.deleteItem(itemToDelete.oid)"
    />
    <StructuredDataViewerDialog
      v-if="showStructuredDataContentViewerDialog"
      v-model:show-dialog="showStructuredDataContentViewerDialog"
      :structured-data-payload="selectedPayload"
    />
  </div>
</template>
