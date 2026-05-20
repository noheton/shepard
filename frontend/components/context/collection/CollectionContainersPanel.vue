<script setup lang="ts">
import type { ContainerSummary } from "@dlr-shepard/backend-client";
import { useFetchCollectionContainers } from "~/composables/context/useFetchCollectionContainers";
import {
  fileContainerPath,
  structureddataContainerPath,
  timeseriesContainerPath,
} from "~/utils/constants";

const props = defineProps<{
  collectionAppId: string | null;
}>();

const collectionAppId = computed(() => props.collectionAppId);
const { containers, isLoading } = useFetchCollectionContainers(collectionAppId);

function containerPath(c: ContainerSummary): string {
  switch (c.containerType) {
    case "TIMESERIES":    return timeseriesContainerPath + c.id + "/";
    case "FILE":          return fileContainerPath + c.id + "/";
    case "STRUCTUREDDATA": return structureddataContainerPath + c.id + "/";
    default:              return "/containers/";
  }
}

function containerIcon(type: string): string {
  switch (type) {
    case "TIMESERIES":    return "mdi-chart-line";
    case "FILE":          return "mdi-folder-outline";
    case "STRUCTUREDDATA": return "mdi-table-large";
    default:              return "mdi-database-outline";
  }
}

function containerLabel(type: string): string {
  switch (type) {
    case "TIMESERIES":    return "Timeseries";
    case "FILE":          return "File";
    case "STRUCTUREDDATA": return "Structured Data";
    default:              return "Container";
  }
}
</script>

<template>
  <div>
    <div
      v-if="isLoading"
      class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-2"
    >
      <v-progress-circular indeterminate size="14" width="2" />
      Loading…
    </div>
    <div
      v-else-if="containers.length === 0"
      class="text-medium-emphasis text-body-2 pa-2"
    >
      No containers referenced by this collection yet.
    </div>
    <v-list v-else density="compact" class="pa-0">
      <v-list-item
        v-for="c in containers"
        :key="c.id"
        :to="containerPath(c)"
        :prepend-icon="containerIcon(c.containerType)"
        :title="c.name ?? `Container ${c.id}`"
        :subtitle="containerLabel(c.containerType)"
        rounded
      />
    </v-list>
  </div>
</template>
