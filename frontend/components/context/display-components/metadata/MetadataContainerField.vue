<script setup lang="ts">
import type { ContainerType } from "@dlr-shepard/backend-client";
import { buildContainerPathByAppId } from "~/utils/buildContainerPath";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";

export interface MetadataContainerFieldProps {
  containerName: string;
  containerId: number;
  containerType: ContainerType;
  availability: "available" | "deleted" | "forbidden" | "error";
  /** When provided, routes the link via appId and suppresses the raw numeric ID display. */
  containerAppId?: string;
}

const props = defineProps<MetadataContainerFieldProps>();
const { advancedMode } = useAdvancedMode();

const containerPath = props.containerAppId
  ? buildContainerPathByAppId(props.containerType, props.containerAppId)
  : buildContainerPath(props.containerType, props.containerId);
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
      <!-- Show numeric ID only in advanced mode when no appId is available -->
      <span v-if="advancedMode && !containerAppId" class="text-no-wrap">(ID: {{ containerId }})</span>
    </div>
  </div>
</template>
