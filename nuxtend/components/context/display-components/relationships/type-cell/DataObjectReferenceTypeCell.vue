<script setup lang="ts">
import type { DataObjectReferenceType } from "../relationshipTableElement";

defineProps<{
  value: DataObjectReferenceType;
}>();
</script>

<template>
  <template v-if="value.availability === 'deleted'">
    Data Object
    <span class="text-error">(Deleted)</span>
  </template>
  <template v-else-if="value.availability === 'private'">
    <div>Data Object (ID: {{ value.id }})</div>
    <div
      class="text-textbody2 text-body-2"
      :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
    >
      In private collection
    </div>
  </template>
  <template v-else>
    <div>Data Object (ID: {{ value.id }})</div>
    <div
      class="text-textbody2 text-body-2"
      :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
    >
      In Collection "{{ value.collectionName }}" (ID: {{ value.collectionId }})
    </div>
  </template>
</template>
