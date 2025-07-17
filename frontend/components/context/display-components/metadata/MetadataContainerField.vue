<script setup lang="ts">
import type { ContainerType } from "@dlr-shepard/backend-client";

export interface MetadataContainerFieldProps {
  containerName: string;
  containerId: number;
  containerType: ContainerType;
  availability: "available" | "deleted" | "forbidden" | "error";
}

const props = defineProps<MetadataContainerFieldProps>();

const containerPath = buildContainerPath(
  props.containerType,
  props.containerId,
);
</script>

<template>
  <div class="d-flex">
    <div class="d-flex flex-column font-weight-bold pr-1 text-no-wrap">
      <span>Container:</span>
      <span v-if="availability !== 'available'" class="text-error">
        ({{ availability }})
      </span>
    </div>
    <div class="d-flex flex-column">
      <div v-if="isDeleted(containerId)">{{ containerName }}</div>
      <a v-else class="text-no-wrap" :href="containerPath" target="_blank">
        {{ containerName }}
      </a>
      <span class="text-no-wrap">(ID: {{ containerId }})</span>
    </div>
  </div>
</template>
