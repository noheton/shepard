<script setup lang="ts">
defineProps<{ collectionId: number }>();

const parentId = defineModel<number | null>("parentId", {
  required: true,
});
</script>

<template>
  <SearchAutocomplete
    input-label="Parent"
    :initial-data-object-id="parentId"
    :collection-id="collectionId"
    @search-ended="id => (parentId = id)"
  />
</template>
