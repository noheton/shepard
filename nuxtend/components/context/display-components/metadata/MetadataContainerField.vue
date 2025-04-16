<script setup lang="ts">
export interface MetadataContainerFieldProps {
  containerName: string;
  containerId: number;
  containerPath: string;
  availability: "available" | "deleted" | "forbidden" | "error";
}

defineProps<MetadataContainerFieldProps>();
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
      <a class="text-no-wrap" :href="containerPath" target="_blank">
        {{ containerName }}
      </a>
      <span class="text-no-wrap">(ID: {{ containerId }})</span>
    </div>
  </div>
</template>
