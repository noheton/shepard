<script lang="ts" setup>
import DeleteContainerButton from "~/components/container/DeleteContainerButton.vue";
import { TimeseriesContainerAccessor } from "~/composables/container/TimeseriesContainerAccessor";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";
import { useTimeseriesContainerLinkedDataObjects } from "~/composables/containers/useTimeseriesContainerLinkedDataObjects";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.TIMESERIES;

const containerAccessor = new TimeseriesContainerAccessor(containerId);
const { dataObjects: linkedDataObjects, isLoading: linkedDataObjectsLoading } =
  useTimeseriesContainerLinkedDataObjects(containerId);

const fetchData = () => {
  containerAccessor.fetchData();
  containerAccessor.fetchMeasurements();
  containerAccessor.fetchRoles();
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
  return containerAccessor.uploadMeasurements(file);
};

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
                :n-items="containerAccessor.measurements.value.length"
                :name="containerAccessor.container.value.name"
                :type-label="'Timeseries Container'"
              >
                <template #buttons>
                  <UploadFilesButton
                    v-if="containerAccessor.isAllowedToEditData.value"
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
      <TimeseriesMeasurementsTable
        :is-allowed-to-edit-data="containerAccessor.isAllowedToEditData.value"
        :measurements="containerAccessor.measurements.value"
      />
      <!-- CC1b: Referenced by — wired to GET /v2/timeseries-containers/{id}/linked-data-objects -->
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
  </div>
</template>
