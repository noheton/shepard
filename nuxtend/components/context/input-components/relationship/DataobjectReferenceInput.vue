<script setup lang="ts">
import type { DataObjectAutocomplete } from "#components";
import type { DataObjectReferenceData } from "../../display-components/relationships/add-dialog/relationshipTypes";

const dataObjectModel = defineModel<DataObjectReferenceData>({
  required: true,
});
const collectionId = ref<number | undefined>();

// get reference of dataobject autocomplete field to clear its input
const dataObjectSearchField =
  ref<ComponentPublicInstance<typeof DataObjectAutocomplete>>();
</script>

<template>
  <div>
    <v-row class="text-textbody1 text-subtitle-2">Choose Data Object</v-row>
    <v-row class="pt-5">
      <CollectionAutocomplete
        class="w-100"
        @search-ended="
          value => {
            collectionId = value?.id;
            // Reset data object values after selecting collection
            dataObjectModel.referencedDataObjectId = undefined;
            dataObjectModel.referenceName = undefined;
            if (dataObjectSearchField) {
              dataObjectSearchField.clearInput();
            }
          }
        "
      />
    </v-row>
    <v-row class="pt-3">
      <DataObjectAutocomplete
        ref="dataObjectSearchField"
        input-label="Data Object ID or Name...*"
        :is-disabled="collectionId === undefined"
        :collection-id="collectionId ?? -1"
        @search-ended="
          value => {
            dataObjectModel.referencedDataObjectId = value?.id;
            dataObjectModel.referenceName = value?.name;
          }
        "
      />
    </v-row>
    <v-row class="pt-3">
      <v-text-field
        v-model:model-value="dataObjectModel.referenceName"
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
        v-model:model-value="dataObjectModel.relationshipName"
        label="Custom Relationship Type"
        variant="outlined"
        density="compact"
        color="primary"
        hide-details
      />
    </v-row>
  </div>
</template>
