<script setup lang="ts">
import type { DataTableElement } from "./dataTableElement";

defineProps<{
  meta: DataTableElement["meta"];
}>();
</script>

<template>
  <div>ID: {{ meta.id }}</div>

  <div
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    <template v-if="meta.referencedContainerAvailability === 'available'">
      Container: {{ meta.referencedContainerName }} (ID: {{ meta.containerId }})
    </template>
    <template v-else-if="meta.referencedContainerAvailability === 'deleted'">
      Container:
      <span class="text-error">(Deleted)</span>
    </template>
    <template v-else-if="meta.referencedContainerAvailability === 'forbidden'">
      Container:
      <span class="text-error">(Private)</span>
      (ID: {{ meta.containerId }})
    </template>
    <template v-else-if="meta.referencedContainerAvailability === 'error'">
      Container:
      <span class="text-error">(Error fetching container)</span>
      (ID: {{ meta.containerId }})
    </template>
  </div>
  <div
    v-if="meta.interval"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Interval: {{ meta.interval }}
  </div>
  <div
    v-if="meta.fileCount"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Files: {{ meta.fileCount }}
  </div>
  <div
    v-if="meta.payloadCount"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    Payloads: {{ meta.payloadCount }}
  </div>
</template>
