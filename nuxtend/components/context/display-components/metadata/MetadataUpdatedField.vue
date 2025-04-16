<script setup lang="ts">
export interface MetadataUpdatedFieldProps {
  updatedAt: Date;
  updatedBy: string;
}

const props = defineProps<MetadataUpdatedFieldProps>();

const createdAtString = toShortDateString(props.updatedAt);
const createdByString = "by " + props.updatedBy;
</script>

<template>
  <div class="d-flex">
    <span class="font-weight-bold pr-1 text-no-wrap">Updated:</span>
    <div class="d-flex flex-column">
      <span class="text-no-wrap">{{ createdAtString }}</span>
      <span class="text-no-wrap">{{ createdByString }}</span>
    </div>
  </div>
</template>
