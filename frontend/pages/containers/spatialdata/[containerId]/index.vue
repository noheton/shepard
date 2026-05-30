<script setup lang="ts">
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
  <PageShell>
    <v-container fluid class="pa-0">
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
              :status="containerAccessor.spatialData.value.status"
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
      <!-- UX-SPATIAL-VIEWER-OR-BANNER: spatial viewer is in development (SPATIAL-V6).
           This banner replaces a blank page so users know the container exists and
           data is stored — the viewer is just not ready yet. -->
      <v-col cols="12" class="mt-4">
        <v-alert
          type="info"
          variant="tonal"
          prepend-icon="mdi-map-marker-outline"
          title="Spatial data viewer — in development (SPATIAL-V6)"
          text="The in-browser viewer for spatial / GIS data is not yet available. The container and its stored data are intact. Download the raw payload or check back when SPATIAL-V6 ships."
        />
      </v-col>
    </v-row>
    <NotFoundPanel v-else-if="isFetchError" />
    <CenteredLoadingSpinner v-else />
    </v-container>
  </PageShell>
</template>
