<script lang="ts" setup>
import DeleteContainerButton from "~/components/container/DeleteContainerButton.vue";
import { TimeseriesContainerAccessor } from "~/composables/container/TimeseriesContainerAccessor";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.TIMESERIES;

const container = new TimeseriesContainerAccessor(containerId);

const fetchData = () => {
  container.fetchData();
  container.fetchMeasurements();
  container.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});

const filterFiles = (files: File[]) => {
  return files.filter(file => {
    const fileName = file.name.toLowerCase();
    return fileName.endsWith(".csv");
  });
};

const uploadFile = async (file: File): Promise<void> => {
  return container.uploadMeasurements(file);
};

fetchData();
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container fluid>
      <v-row v-if="!!container.container.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: container.container.value.name,
                to: containersPath + urlSegment + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <ContainerTitleAndMetadataDisplay
                :id="container.container.value.id"
                :n-items="container.measurements.value.length"
                :name="container.container.value.name"
                :type-label="'Timeseries Container'"
              >
                <template #buttons>
                  <UploadFilesButton
                    v-if="container.isAllowedToEditData.value"
                    accept=".csv"
                    button-text="Upload CSV"
                    dialog-title="Upload CSV"
                    :filter="filterFiles"
                    :upload-file="uploadFile"
                    @upload-finished="() => fetchData()"
                  >
                    <template #info>
                      <TimeseriesFileUploadInfoText />
                    </template>
                  </UploadFilesButton>
                  <EditPermissionsButton
                    v-if="container.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="container"
                  />
                  <DeleteContainerButton
                    v-if="container.isAllowedToDelete.value"
                    :entity-name="container.container.value.name"
                    @delete="container.delete()"
                  />
                </template>
              </ContainerTitleAndMetadataDisplay>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
      <TimeseriesMeasurementsTable
        :is-allowed-to-edit-data="container.isAllowedToEditData.value"
        :measurements="container.measurements.value"
      />
    </v-container>
  </div>
</template>
