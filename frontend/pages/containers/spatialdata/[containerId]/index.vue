<script setup lang="ts">
import { SpatialDataContainerAccessor } from "~/composables/container/SpatialDataContainerAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.SPATIALDATA;

const containerAccessor = new SpatialDataContainerAccessor(containerId);
const fetchData = () => {
  containerAccessor.fetchData();
  containerAccessor.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});
fetchData();

watch(containerAccessor.spatialData, () => {
  useHead({
    title: containerAccessor.spatialData.value?.name + " | shepard",
  });
});
</script>
<template>
  <v-container fluid style="max-width: 1200px; margin: auto">
    <v-row v-if="!!containerAccessor.spatialData.value" no-gutters>
      <v-col cols="12">
        <Breadcrumbs
          :items="[
            {
              title: 'Containers',
              to: containersPath,
            },
            {
              title: containerAccessor.spatialData.value.name,
              to: containersPath + urlSegment + containerId,
            },
          ]"
        />
      </v-col>
      <v-col cols="12">
        <v-container class="pa-0" fluid>
          <v-row no-gutters>
            <ContainerTitleAndMetadataDisplay
              :id="containerAccessor.spatialData.value.id"
              :name="containerAccessor.spatialData.value.name"
              :type-label="'Spatial Data Container'"
            >
              <template #buttons>
                <EditPermissionsButton
                  v-if="containerAccessor.isAllowedToEditPermissions.value"
                  :shepard-object-accessor="containerAccessor"
                />
                <DeleteContainerButton
                  v-if="containerAccessor.isAllowedToDelete.value"
                  :entity-name="containerAccessor.spatialData.value.name"
                  @delete="containerAccessor.delete()"
                />
              </template>
            </ContainerTitleAndMetadataDisplay>
          </v-row>
        </v-container>
      </v-col>
    </v-row>
    <CenteredLoadingSpinner v-else />
  </v-container>
</template>
