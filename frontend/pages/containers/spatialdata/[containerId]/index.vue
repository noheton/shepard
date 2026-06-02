<script setup lang="ts">
import { computed } from "vue";
import type { SpatialDataPoint } from "@dlr-shepard/backend-client";
import { SpatialDataContainerApi } from "@dlr-shepard/backend-client";
import { SpatialDataContainerAccessor } from "~/composables/container/SpatialDataContainerAccessor";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { SpatialPoint } from "~/utils/spatialDownsample";
// SpatialRenderMode is the union type "pointcloud" | "trajectory" exported
// from SpatialPointsCanvas.vue. We import it as a local type alias to keep
// the page's <script setup> portable without a SFC import in the type-graph.
type SpatialRenderMode = "pointcloud" | "trajectory";

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

// MFFD W7 / GAP-5: in-page 3D viewer for the container's payload. Fetches
// up to ``maxPoints`` points from the existing payload endpoint and feeds
// them to ``SpatialPointsCanvas``. The render mode is inferred from the
// container name (suffix "trajectory" → trajectory, else pointcloud) until
// the dedicated ``kind`` property lands on SpatialDataContainerIO.
const points = ref<SpatialPoint[]>([]);
const renderMode = ref<SpatialRenderMode>("pointcloud");
const isLoadingPoints = ref(false);
const pointsError = ref<string | null>(null);

const inferredMode = computed<SpatialRenderMode>(() => {
  const name = (containerAccessor.spatialData.value?.name ?? "").toLowerCase();
  if (name.includes("fsd course") || name.includes("trajectory")) {
    return "trajectory";
  }
  return "pointcloud";
});

async function fetchPoints() {
  if (containerAccessor.spatialData.value == null) return;
  renderMode.value = inferredMode.value;
  isLoadingPoints.value = true;
  pointsError.value = null;
  try {
    const api = useShepardApi(SpatialDataContainerApi).value;
    const dataPoints = await api.getSpatialDataPoints({
      spatialDataContainerId: containerId,
      limit: 500_000,
    });
    points.value = dataPoints.map((p: SpatialDataPoint) => ({
      x: p.x,
      y: p.y,
      z: p.z,
      value: undefined,
      t: p.timestamp ?? undefined,
    }));
  } catch (err) {
    pointsError.value = err instanceof Error ? err.message : String(err);
    points.value = [];
  } finally {
    isLoadingPoints.value = false;
  }
}

watch(
  () => containerAccessor.spatialData.value?.id,
  () => {
    if (containerAccessor.spatialData.value) fetchPoints();
  },
  { immediate: true },
);

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
      <!-- MFFD W7 / GAP-5: in-browser 3D viewer for the spatial payload. -->
      <v-col cols="12" class="mt-4" data-testid="spatial-viewer">
        <div
          v-if="isLoadingPoints"
          class="d-flex justify-center align-center"
          style="height: 500px; background: #0d0d0d; border-radius: 8px;"
        >
          <v-progress-circular indeterminate color="primary" />
        </div>
        <v-alert
          v-else-if="pointsError"
          type="warning"
          variant="tonal"
          :text="`Could not load spatial points: ${pointsError}`"
        />
        <v-alert
          v-else-if="points.length === 0"
          type="info"
          variant="tonal"
          prepend-icon="mdi-map-marker-outline"
          title="No spatial points in this container"
          text="The container exists but has not yet been populated. Run the spatial-importer pass to fill it from a TPS / FSD source file."
        />
        <SpatialPointsCanvas
          v-else
          :points="points"
          :mode="renderMode"
          :label="renderMode === 'trajectory' ? 'Time' : 'Z (height)'"
          :colormap="renderMode === 'trajectory' ? 'plasma' : 'viridis'"
          data-testid="spatial-points-canvas"
        />
      </v-col>
    </v-row>
    <NotFoundPanel v-else-if="isFetchError" />
    <CenteredLoadingSpinner v-else />
    </v-container>
  </PageShell>
</template>
