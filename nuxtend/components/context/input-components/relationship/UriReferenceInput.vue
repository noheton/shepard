<script setup lang="ts">
import type { URIReferenceData } from "../../display-components/relationships/add-dialog/relationshipTypes";

const uriModel = defineModel<URIReferenceData>({ required: true });
</script>

<template>
  <div>
    <v-row class="text-textbody1 text-subtitle-2">Choose Link</v-row>

    <v-row class="pt-5">
      <v-text-field
        v-model:model-value="uriModel.relationshipName"
        label="Custom Relationship Type"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
      />
    </v-row>

    <v-row class="pt-4">
      <v-text-field
        v-model:model-value="uriModel.referenceURI"
        label="Paste URI...*"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
        required
      />
    </v-row>

    <v-row class="pt-4">
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
  </div>
</template>
