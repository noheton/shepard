<script setup lang="ts">
import type { RelationshipTableElement } from "./relationshipTableElement";

defineProps<{
  value: RelationshipTableElement["relationship"];
}>();
</script>

<template>
  <div v-if="!!value" :style="{ fontWeight: 600 }">{{ value }}</div>
  <div v-else>(Unspecified)</div>
</template>
