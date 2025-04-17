<script setup lang="ts">
import type { CollectionReferenceData } from "../../display-components/relationships/add-dialog/relationshipTypes";

const collectionModel = defineModel<CollectionReferenceData>({
  required: true,
});
</script>

<template>
  <div>
    <v-row class="text-textbody1 text-subtitle-2">Choose Collection</v-row>
    <v-row class="pt-5">
      <CollectionAutocomplete
        class="w-100"
        @search-ended="
          value => {
            collectionModel.referencedCollectionId = value?.id;
            collectionModel.referenceName = value?.name;
          }
        "
      />
    </v-row>
    <v-row class="pt-3">
      <v-text-field
        v-model:model-value="collectionModel.referenceName"
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
        v-model:model-value="collectionModel.relationshipName"
        label="Custom Relationship Type"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
      />
    </v-row>
  </div>
</template>
