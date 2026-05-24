<script setup lang="ts">
/**
 * Empty-state placeholder used across list / table panels.
 *
 * `label` is the primary line (always shown). `hint` is an optional
 * second line in a quieter style, intended to explain *why* the list
 * is empty when the bare label could be misread as a defect — e.g.
 * "Provenance capture is currently scoped to writes; reads don't
 * appear here yet." (See RDM-2026-05-24-004 closure.)
 */
defineProps<{ label: string; hint?: string }>();
</script>

<template>
  <div class="text-center">
    <v-img
      src="../../assets/empty_list.svg"
      height="77"
      width="77"
      class="mx-auto"
    />
    <div>
      <span class="text-body-2 text-textbody2">{{ label }}</span>
    </div>
    <div v-if="hint" class="mt-1 px-4">
      <span class="text-caption text-medium-emphasis">{{ hint }}</span>
    </div>
  </div>
</template>
