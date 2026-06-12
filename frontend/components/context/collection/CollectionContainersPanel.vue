<script setup lang="ts">
import type { ContainerSummaryIO } from "@dlr-shepard/backend-client";
import { useFetchCollectionContainers } from "~/composables/context/useFetchCollectionContainers";
import {
  iconForContainerType,
  labelForContainerType,
  urlSegmentForContainerType,
} from "~/utils/containerTypeRegistry";

const props = defineProps<{
  collectionAppId: string | null;
}>();

const collectionAppId = computed(() => props.collectionAppId);
const { containers, isLoading } = useFetchCollectionContainers(collectionAppId);

function containerPath(c: ContainerSummaryIO): string {
  // V2-SWEEP-003-2: route by appId; accessors handle UUID (v2 GET) and numeric (V1-EXCEPTION).
  return `/containers/${urlSegmentForContainerType(c.containerType ?? "")}${c.appId ?? c.id}/`;
}

function containerIcon(type: string | undefined): string {
  return iconForContainerType(type ?? "");
}

function containerLabel(type: string | undefined): string {
  return labelForContainerType(type ?? "");
}
</script>

<template>
  <div>
    <div
      v-if="isLoading"
      role="status"
      class="d-flex align-center ga-2 text-medium-emphasis text-body-2 pa-2"
    >
      <v-progress-circular indeterminate size="14" width="2" aria-hidden="true" />
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
        :key="c.appId ?? c.id"
        :to="containerPath(c)"
        :prepend-icon="containerIcon(c.containerType)"
        :title="c.name ?? `Container ${c.appId ?? c.id}`"
        :subtitle="containerLabel(c.containerType)"
        rounded
      />
    </v-list>
  </div>
</template>
