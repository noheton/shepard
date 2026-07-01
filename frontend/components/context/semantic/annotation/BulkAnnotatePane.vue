<script setup lang="ts">
/**
 * SEMANTIC-ANNOTATE-BULK-REST-1 — placeholder stub for the bulk annotation UI.
 *
 * The bulk REST endpoint `POST /v2/annotations/bulk` is now live (up to 100
 * annotations per call, best-effort per row, same wire shape as the single
 * POST /v2/annotations). This component is the minimum frontend surface
 * required by CLAUDE.md §"ship a UI stub for every backend feature".
 *
 * The full multi-select annotation flow (select N DataObjects in the
 * collections list → open bulk dialog → annotate all) is tracked as a
 * follow-up under SEMANTIC-ANNOTATE-BULK-UI-1 in aidocs/16.
 */

const props = defineProps<{
  /** Optional endpoint base URL hint shown in the code snippet. */
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
        Annotate multiple entities in a single REST call
      </v-card-subtitle>
    </v-card-item>

    <v-card-text>
      <p class="text-body-2 text-medium-emphasis mb-3">
        Use <code>POST {{ endpoint }}</code> to create up to 100 semantic
        annotations in one round-trip. Each entry uses the same shape as the
        single <code>POST /v2/annotations</code> endpoint; failed rows are
        recorded per-row without aborting the rest of the batch.
      </p>
      <v-alert
        type="info"
        variant="tonal"
        density="compact"
        icon="mdi-clock-outline"
      >
        A multi-select annotation dialog in the DataObject list is tracked as
        <strong>SEMANTIC-ANNOTATE-BULK-UI-1</strong> in the backlog.
      </v-alert>
    </v-card-text>
  </v-card>
</template>
