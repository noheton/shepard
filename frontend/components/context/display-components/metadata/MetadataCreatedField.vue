<script setup lang="ts">
export interface MetadataCreatedFieldProps {
  createdAt: Date;
  createdBy: string;
}

const props = defineProps<MetadataCreatedFieldProps>();

const createdAtString = toShortDateString(props.createdAt);
const createdByString = "by " + props.createdBy;
</script>

<template>
  <div class="d-flex">
    <span class="font-weight-bold pr-1 text-no-wrap">Created:</span>
    <div class="d-flex flex-column">
      <span class="text-no-wrap">{{ createdAtString }}</span>
      <span class="text-no-wrap">{{ createdByString }}</span>
    </div>
  </div>
</template>
