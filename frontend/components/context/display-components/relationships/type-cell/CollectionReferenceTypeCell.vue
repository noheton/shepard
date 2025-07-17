<script setup lang="ts">
import type { CollectionReferenceType } from "../relationshipTableElement";

defineProps<{
  value: CollectionReferenceType;
}>();
</script>

<template>
  <template v-if="value.availability === 'deleted'">
    Collection
    <span class="text-error">(Deleted)</span>
  </template>
  <template v-else>
    <div>Collection (ID: {{ value.id }})</div>
  </template>
</template>
