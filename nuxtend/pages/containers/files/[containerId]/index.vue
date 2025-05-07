<script setup lang="ts">
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

const showFileUploadDialog = ref<boolean>(false);

const uploadFile = async (file: File) => {
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
                <v-btn
                  v-if="containerAccessor.isAllowedToEditData.value"
                  color="primary"
                  variant="flat"
                  prepend-icon="mdi-plus-circle"
                  @click="showFileUploadDialog = true"
                >
                  Add File
                </v-btn>
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
    <FileUploadDialog
      v-if="showFileUploadDialog"
      v-model:show-dialog="showFileUploadDialog"
      :upload-file="uploadFile"
      :accessor="containerAccessor"
    />
  </v-container>
</template>
