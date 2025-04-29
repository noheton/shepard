<script setup lang="ts">
import type { ShepardFile } from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.FILE;

const containerAccessor = new FileContainerAccessor(containerId);
containerAccessor.fetchData();
containerAccessor.fetchFiles();
containerAccessor.fetchRoles();

const fileToDelete = ref<ShepardFile | undefined>(undefined);
const showFileDeleteConfirmDialog = ref<boolean>(false);

const deleteFile = (file: ShepardFile) => {
  fileToDelete.value = file;
  showFileDeleteConfirmDialog.value = true;
};
</script>
<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
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
                    rounded="lg"
                    variant="flat"
                    color="primary"
                    prepend-icon="mdi-tray-arrow-up"
                  >
                    upload file
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
        @delete-file="file => deleteFile(file)"
        @download-file="file => containerAccessor.downloadFile(file)"
      />
    </v-container>
    <ConfirmSafeDeleteDialog
      v-if="showFileDeleteConfirmDialog && fileToDelete?.filename"
      v-model:show-dialog="showFileDeleteConfirmDialog"
      :target-name="fileToDelete?.filename"
      entityType="file"
      @confirmed="containerAccessor.deleteFile(fileToDelete)"
    />
  </div>
</template>
