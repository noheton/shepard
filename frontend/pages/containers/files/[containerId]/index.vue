<script setup lang="ts">
import type { ShepardFile } from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";
import { useFileContainerLinkedDataObjects } from "~/composables/containers/useFileContainerLinkedDataObjects";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.FILE;

const containerAccessor = new FileContainerAccessor(containerId);
const { dataObjects: linkedDataObjects, isLoading: linkedDataObjectsLoading } =
  useFileContainerLinkedDataObjects(containerId);

const fetchData = () => {
  containerAccessor.fetchData();
  containerAccessor.fetchFiles();
  containerAccessor.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});

const uploadFile = async (file: File): Promise<ShepardFile> => {
  return containerAccessor.uploadFile(file);
};
fetchData();

watch(containerAccessor.fileContainer, () => {
  useHead({
    title: containerAccessor.fileContainer.value?.name + " | shepard",
  });
});
</script>
<template>
  <v-container fluid style="max-width: 1200px; margin: auto">
    <v-row v-if="!!containerAccessor.fileContainer.value" no-gutters>
      <v-col cols="12">
        <Breadcrumbs
          :items="[
            {
              title: 'Containers',
              to: containersPath,
            },
            {
              title: containerAccessor.fileContainer.value.name,
              to: containersPath + urlSegment + containerId,
            },
          ]"
        />
      </v-col>
      <v-col cols="12">
        <v-container class="pa-0" fluid>
          <v-row no-gutters>
            <ContainerTitleAndMetadataDisplay
              :id="containerAccessor.fileContainer.value.id"
              :n-items="containerAccessor.files.value?.length"
              :name="containerAccessor.fileContainer.value.name"
              :type-label="'File Container'"
            >
              <template #buttons>
                <UploadFilesButton
                  v-if="containerAccessor.isAllowedToEditData.value"
                  :upload-file="
                    async file => {
                      uploadFile(file);
                    }
                  "
                />
                <EditPermissionsButton
                  v-if="containerAccessor.isAllowedToEditPermissions.value"
                  :shepard-object-accessor="containerAccessor"
                />
                <DeleteContainerButton
                  v-if="containerAccessor.isAllowedToDelete.value"
                  :entity-name="containerAccessor.fileContainer.value.name"
                  @delete="containerAccessor.delete()"
                />
              </template>
            </ContainerTitleAndMetadataDisplay>
          </v-row>
        </v-container>
      </v-col>
    </v-row>
    <CenteredLoadingSpinner v-else />
    <FilesTable
      :files="containerAccessor.files.value"
      :is-allowed-to-edit="containerAccessor.isAllowedToEditData.value"
      :loading="containerAccessor.loading.value"
      @delete-file="(file: ShepardFile) => containerAccessor.deleteFile(file)"
      @download-file="
        (file: ShepardFile) => containerAccessor.downloadFile(file)
      "
    />
    <!-- CC1b: Referenced by — wired to GET /v2/file-containers/{id}/linked-data-objects -->
    <ExpansionPanels class="mt-4" :default-open="[0]">
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
</template>
