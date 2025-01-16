<script setup lang="ts">
import type { RelationshipTableElement } from "./relationshipTableElement";

defineProps<{
  value: RelationshipTableElement["type"];
}>();
</script>

<template>
  <div>
    {{ value.value }}
  </div>
  <div
    v-if="!!value.collection"
    class="text-textbody2 text-body-2"
    :style="{ fontSize: '0.75rem !important', lineHeight: '16px' }"
  >
    {{ value.collection }}
  </div>
</template>
