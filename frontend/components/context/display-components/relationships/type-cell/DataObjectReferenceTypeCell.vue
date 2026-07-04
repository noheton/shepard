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
    <div>Data Object<template v-if="'id' in value"> (ID: {{ value.id }})</template></div>
    <div
      class="text-textbody2 text-body-2"
      :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
    >
      <template v-if="value.collectionName">
        In Collection "{{ value.collectionName }}"<template v-if="'collectionId' in value"> (ID: {{ value.collectionId }})</template>
      </template>
    </div>
  </template>
</template>
