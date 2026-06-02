<script setup lang="ts">
/**
 * DataObjectSpatialContainersPane — lists SpatialDataReferences on a
 * DataObject, grouped by kind, with an "Open in 3D viewer" button per row.
 *
 * MFFD W7 / GAP-5 — the first user-visible surface for the spatial substrate
 * promotion. Mounts on the DataObject detail page after the file-references
 * panel. Each row links into the existing container detail page at
 * ``/containers/spatialdata/{containerId}`` which now renders the pointcloud
 * via ``SpatialPointsCanvas.vue``.
 *
 * The pane is read-only in this PR (list + open). Create/Edit/Delete affordances
 * are tracked under ``REF-EDIT-SPATIAL`` in ``aidocs/16-dispatcher-backlog.md``;
 * promotions from FileReferences are the canonical create path (via the
 * Python importer, ``plugins/spatial-importer/``).
 */
import type { SpatialDataReference } from "@dlr-shepard/backend-client";
import { useSpatialDataReferencesForDataObject } from "~/composables/context/useSpatialDataReferencesForDataObject";
import {
  inferSpatialKindFromName,
  SPATIAL_KIND_ICONS,
  SPATIAL_KIND_LABELS,
  type SpatialKind,
} from "~/utils/spatialKind";

const props = defineProps<{
  collectionId: number;
  dataObjectId: number;
}>();

const { references, isLoading } = useSpatialDataReferencesForDataObject(
  props.collectionId,
  props.dataObjectId,
);

/**
 * Group references by their underlying SpatialDataContainer kind hint
 * (profile, trajectory, brush-trace). The classifier is in
 * ``~/utils/spatialKind`` so it can be unit-tested without a Vue SFC loader.
 */
const grouped = computed<Record<SpatialKind, SpatialDataReference[]>>(() => {
  const buckets: Record<SpatialKind, SpatialDataReference[]> = {
    profile: [],
    trajectory: [],
    "brush-trace": [],
    other: [],
  };
  for (const ref of references.value) {
    const kind = inferSpatialKindFromName(ref.name);
    buckets[kind].push(ref);
  }
  return buckets;
});

const nonEmptyKinds = computed<SpatialKind[]>(() =>
  (Object.keys(grouped.value) as SpatialKind[]).filter(
    k => grouped.value[k].length > 0,
  ),
);

const totalCount = computed(() => references.value.length);
</script>

<template>
  <v-card variant="outlined" class="pa-3" data-testid="spatial-containers-pane">
    <v-card-title class="d-flex align-center pa-0 mb-2">
      <v-icon class="mr-2">mdi-map-marker-outline</v-icon>
      <span>Spatial data</span>
      <v-chip
        v-if="totalCount > 0"
        size="small"
        class="ml-2"
        data-testid="spatial-count-chip"
      >{{ totalCount }}</v-chip>
    </v-card-title>

    <div v-if="isLoading" class="d-flex justify-center my-4">
      <v-progress-circular indeterminate size="24" />
    </div>

    <div
      v-else-if="totalCount === 0"
      class="text-medium-emphasis my-3"
      data-testid="spatial-empty-state"
    >
      No spatial data containers on this DataObject. Run the spatial-importer
      pass to promote pointcloud / trajectory file references into
      SpatialDataContainers.
    </div>

    <div v-else>
      <div
        v-for="kind in nonEmptyKinds"
        :key="kind"
        class="mb-3"
        :data-testid="`spatial-kind-${kind}`"
      >
        <div class="d-flex align-center mb-1">
          <v-icon size="18" class="mr-1">{{ SPATIAL_KIND_ICONS[kind] }}</v-icon>
          <span class="font-weight-medium">{{ SPATIAL_KIND_LABELS[kind] }}</span>
          <v-chip size="x-small" class="ml-2">{{ grouped[kind].length }}</v-chip>
        </div>
        <v-list density="compact" class="pa-0">
          <v-list-item
            v-for="ref in grouped[kind]"
            :key="ref.id"
            :data-testid="`spatial-ref-${ref.id}`"
          >
            <template #prepend>
              <v-icon size="18">{{ SPATIAL_KIND_ICONS[kind] }}</v-icon>
            </template>
            <v-list-item-title>{{ ref.name }}</v-list-item-title>
            <v-list-item-subtitle v-if="ref.spatialDataContainerId">
              Container #{{ ref.spatialDataContainerId }}
            </v-list-item-subtitle>
            <template #append>
              <v-btn
                v-if="ref.spatialDataContainerId"
                size="small"
                variant="tonal"
                :to="`/containers/spatialdata/${ref.spatialDataContainerId}`"
                data-testid="open-spatial-viewer"
                prepend-icon="mdi-cube-scan"
              >
                Open in 3D viewer
              </v-btn>
            </template>
          </v-list-item>
        </v-list>
      </div>
    </div>
  </v-card>
</template>
