<script lang="ts" setup>
import { TimeseriesContainerAccessor } from "~/composables/shepardObjectAccessor";
import { containerTypeUrlPathSegmentMappings } from "~/utils/containerPathMappings";
import DeleteContainerButton from "~/components/container/DeleteContainerButton.vue";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.TIMESERIES;

const container = new TimeseriesContainerAccessor(containerId);
container.fetchData();
container.fetchMeasurementsTable();
container.fetchRoles();

onContainerUpdated(() => {
  container.fetchData();
  container.fetchMeasurementsTable();
  container.fetchRoles();
});
</script>

<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container class="pa-0 fill-height" fluid>
      <v-row v-if="!!container.timeseries.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: container.timeseries.value.name,
                to: containersPath + urlSegment + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <ContainerTitleAndMetadataDisplay
                :id="container.timeseries.value.id"
                :n-items="container.measurements.value.length"
                :name="container.timeseries.value.name"
                :type-label="'Timeseries Container'"
              >
                <template #buttons>
                  <EditPermissionsButton
                    v-if="container.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="container"
                  />
                  <DeleteContainerButton
                    v-if="container.isAllowedToDelete.value"
                    :entity-name="container.timeseries.value.name"
                    @delete="container.delete()"
                  />
                </template>
              </ContainerTitleAndMetadataDisplay>
            </v-row>
          </v-container>
        </v-col>
      </v-row>
      <CenteredLoadingSpinner v-else />
    </v-container>
    <TimeseriesMeasurementsTable
      :is-allowed-to-edit-data="container.isAllowedToEditData.value"
      :measurements="container.measurements.value"
    />
  </div>
</template>
