<script setup lang="ts">
import type { ShepardFile } from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.FILE;

const containerAccessor = new FileContainerAccessor(containerId);
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
      @delete-file="file => containerAccessor.deleteFile(file)"
      @download-file="file => containerAccessor.downloadFile(file)"
    />
  </v-container>
</template>
