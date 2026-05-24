<script setup lang="ts">
import type { DataObject } from "@dlr-shepard/backend-client";

defineProps<{
  dataObject: DataObject;
}>();
</script>

<template>
  <v-list-item
    prepend-icon="mdi-database-outline"
    :title="dataObject.name"
    :subtitle="dataObject.status ? `Status: ${dataObject.status}` : undefined"
    class="rounded mb-1"
  >
    <!-- Data object's own semantic annotations, shown inline so users
         scanning the linked-by list can spot tagged datasets at a glance.
         UI-016: cap visible chips at 3 — repeating tags across rows (e.g. the
         same `bench` / `propellant` / `test_engineer` triple on every TR-00x)
         dominate the row otherwise. Click "+N more" to expand inline. -->
    <div class="dataobject-annotations">
      <SemanticAnnotationList
        :annotated="new AnnotatedDataObject(dataObject.collectionId, dataObject.id)"
        :can-delete="false"
        :limit="3"
      />
    </div>
    <template #append>
      <div class="d-flex ga-1">
        <v-btn
          :to="`/collections/${dataObject.collectionId}`"
          variant="text"
          size="x-small"
          icon="mdi-folder-outline"
          title="Open collection"
        />
        <v-btn
          :to="`/collections/${dataObject.collectionId}/dataObjects/${dataObject.id}`"
          variant="text"
          size="x-small"
          icon="mdi-arrow-right"
          title="Open data object"
        />
      </div>
    </template>
  </v-list-item>
</template>

<style scoped>
.dataobject-annotations :deep(ul) {
  gap: 4px 8px;
  margin-top: 2px;
  margin-bottom: 0;
}
.dataobject-annotations :deep(.v-chip) {
  font-size: 0.7rem;
}
</style>
