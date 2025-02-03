<script setup lang="ts">
defineProps<{ collectionId: number; parentId: number | null }>();
const emit = defineEmits<{
  (e: "parentChanged", value: number | null): void;
}>();
</script>

<template>
  <v-row>
    <v-col>
      <DataObjectEditSearchAutocomplete
        input-label="Parent"
        :initial-data-object-id="parentId"
        :collection-id="collectionId"
        @search-ended="id => emit('parentChanged', id)"
      />
    </v-col>
  </v-row>
</template>
