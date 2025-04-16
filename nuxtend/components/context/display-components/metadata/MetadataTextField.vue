<script setup lang="ts">
export interface MetadataTextFieldProps {
  label: string;
  text: string;
}

defineProps<MetadataTextFieldProps>();
</script>

<template>
  <div class="d-flex">
    <span class="font-weight-bold pr-1 text-no-wrap">{{ label }}:</span>
    <span class="text-no-wrap">{{ text }}</span>
  </div>
</template>
