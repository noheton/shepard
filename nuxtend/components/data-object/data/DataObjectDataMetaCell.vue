<script setup lang="ts">
defineProps<{
  id: number;
  containerName: string;
  containerId: number;
  interval?: string;
  fileCount?: number;
}>();
</script>

<template>
  <div>ID: {{ id }}</div>
  <div
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Container: {{ containerName }} (ID: {{ containerId }})
  </div>
  <div
    v-if="interval"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Interval: {{ interval }}
  </div>
  <div
    v-if="fileCount"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Files: {{ fileCount }}
  </div>
</template>
