<script setup lang="ts">
import { SpatialDataContainerAccessor } from "~/composables/container/SpatialDataContainerAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.SPATIALDATA;

const container = new SpatialDataContainerAccessor(containerId);
const fetchData = () => {
  container.fetchData();
  container.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});
fetchData();
</script>
<template>
  <div style="max-width: 1200px; margin: auto">
    <v-container class="pa-0 fill-height" fluid>
      <v-row v-if="!!container.spatialData.value" no-gutters>
        <v-col cols="12">
          <Breadcrumbs
            :items="[
              {
                title: 'Containers',
                to: containersPath,
              },
              {
                title: container.spatialData.value.name,
                to: containersPath + urlSegment + containerId,
              },
            ]"
          />
        </v-col>
        <v-col cols="12">
          <v-container class="pa-0" fluid>
            <v-row no-gutters>
              <ContainerTitleAndMetadataDisplay
                :id="container.spatialData.value.id"
                :name="container.spatialData.value.name"
                :type-label="'Spatial Data Container'"
              >
                <template #buttons>
                  <EditPermissionsButton
                    v-if="container.isAllowedToEditPermissions.value"
                    :shepard-object-accessor="container"
                  />
                  <DeleteContainerButton
                    v-if="container.isAllowedToDelete.value"
                    :entity-name="container.spatialData.value.name"
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
  </div>
</template>
