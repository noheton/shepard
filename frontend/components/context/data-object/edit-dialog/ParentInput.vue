<script setup lang="ts">
defineProps<{
  collectionId: number;
  /** UUID v7 — when supplied, search uses GET /v2/search (SEARCH-V2-3). */
  collectionAppId?: string;
}>();

const parentId = defineModel<number | null>("parentId", {
  required: true,
});
</script>

<template>
  <DataObjectAutocomplete
    input-label="Parent"
    :initial-data-object-id="parentId"
    :collection-id="collectionId"
    :collection-app-id="collectionAppId"
    @search-ended="
      value => {
        if (value) {
          parentId = value.id;
        }
      }
    "
  />
</template>
