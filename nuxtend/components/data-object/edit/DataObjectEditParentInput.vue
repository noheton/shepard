<script setup lang="ts">
defineProps<{ collectionId: number; parentId: number | null }>();
const emit = defineEmits<{
  (e: "parentChanged", value: number | null): void;
}>();
</script>

<template>
  <v-row class="pt-4 pb-4">
    <DataObjectEditSearchAutocomplete
      input-label="Parent"
      :initial-data-object-id="parentId"
      :collection-id="collectionId"
      @search-ended="id => emit('parentChanged', id)"
    />
  </v-row>
</template>
