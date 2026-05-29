<script setup lang="ts">
/**
 * SPATIAL-V6-004: spatial container page now shows the BrushTraceView 3D viewer
 * instead of the "in development" banner.
 */
import { SpatialDataContainerAccessor } from "~/composables/container/SpatialDataContainerAccessor";

const { routeParams } = useContainerRouteParams();
const containerId = routeParams.value.containerId;
const urlSegment = containerTypeUrlPathSegmentMappings.SPATIALDATA;

const containerAccessor = new SpatialDataContainerAccessor(containerId);
const isFetchError = ref(false);
const fetchData = () => {
  isFetchError.value = false;
  containerAccessor.fetchData().catch(() => {
    isFetchError.value = true;
  });
  containerAccessor.fetchRoles();
};

onContainerUpdated(() => {
  fetchData();
});
fetchData();

// UX Pattern F (2026-05-24): reactive title — call useHead once with a getter.
useHead({
  title: () =>
    containerAccessor.spatialData.value?.name
      ? `${containerAccessor.spatialData.value.name} (Spatial Data) — shepard`
      : "Spatial Data Container — shepard",
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
      <!-- SPATIAL-V6-004: BrushTrace 3D viewer — replaces the old "in development" banner. -->
      <v-col cols="12" class="mt-4">
        <ClientOnly>
          <BrushTraceView
            v-if="containerAccessor.spatialData.value.id != null"
            :container-id="containerAccessor.spatialData.value.id"
          />
        </ClientOnly>
      </v-col>
    </v-row>
    <NotFoundPanel v-else-if="isFetchError" />
    <CenteredLoadingSpinner v-else />
  </v-container>
</template>
