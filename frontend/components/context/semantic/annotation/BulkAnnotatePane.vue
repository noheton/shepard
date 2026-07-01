<script setup lang="ts">
/**
 * SEMANTIC-ANNOTATE-BULK-UI-1 — informational pane about bulk annotation.
 *
 * The interactive bulk annotation flow lives in the DataObject list
 * (CollectionDataObjectsPanel): select N rows via checkboxes → "Bulk annotate"
 * button → BulkAnnotationDialog.
 *
 * This pane is kept for developer reference / admin tool tiles.
 */

const props = defineProps<{
  /** Optional endpoint base URL shown in the code snippet. */
  apiBase?: string;
}>();

const endpoint = computed(() => `${props.apiBase ?? ""}/v2/annotations/bulk`);
</script>

<template>
  <v-card variant="outlined">
    <v-card-item>
      <template #prepend>
        <v-icon icon="mdi-tag-multiple-outline" color="primary" size="28" />
      </template>
      <v-card-title>Bulk annotation</v-card-title>
      <v-card-subtitle>
        Annotate multiple entities in a single round-trip
      </v-card-subtitle>
    </v-card-item>

    <v-card-text>
      <v-alert
        type="success"
        variant="tonal"
        density="compact"
        icon="mdi-check-circle-outline"
        class="mb-3"
      >
        <strong>Available in the DataObject list:</strong>
        select rows using the checkboxes, then click
        <strong>Bulk annotate</strong> in the action bar.
      </v-alert>

      <p class="text-body-2 text-medium-emphasis">
        Direct API: <code>POST {{ endpoint }}</code> — up to 100 annotations
        per call, best-effort per row (failed rows record <code>ok=false</code>
        without aborting the rest).
      </p>
    </v-card-text>
  </v-card>
</template>
