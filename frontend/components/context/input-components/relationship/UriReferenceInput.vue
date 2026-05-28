<script setup lang="ts">
import type { URIReferenceData } from "../../display-components/relationships/add-dialog/relationshipTypes";

defineProps<{
  /**
   * REF-EDIT-TPL-6 — Placeholder for the URI input, surfaced from a parent
   * `urn:shepard:reference:uriRelationship` template hint (`uriPrefix`).
   * The hint never overwrites the bound value; it only shapes the empty-state
   * placeholder so the user knows what URI shape is expected (e.g. DOI URL,
   * ORCID prefix). Defaults to "Paste URI...*" when no hint is present.
   */
  uriPlaceholder?: string;
}>();

const uriModel = defineModel<URIReferenceData>({ required: true });
</script>

<template>
  <div>
    <v-row class="text-textbody1 text-subtitle-2">Choose Link</v-row>
    <v-row class="pt-5">
      <v-text-field
        v-model:model-value="uriModel.referenceURI"
        :label="uriPlaceholder ? `Paste URI...* (e.g. ${uriPlaceholder})` : 'Paste URI...*'"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
        required
      />
    </v-row>
    <v-row class="pt-3">
      <v-text-field
        v-model:model-value="uriModel.referenceName"
        label="Name...*"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
        required
      />
    </v-row>
    <v-row class="pt-3">
      <v-text-field
        v-model:model-value="uriModel.relationshipName"
        label="Custom Relationship Type"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
      />
    </v-row>
  </div>
</template>
