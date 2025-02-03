<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useEditCollection } from "./useEditCollection";

interface CollectionEditDescriptionDialogProps {
  collection: Collection;
}

const props = defineProps<CollectionEditDescriptionDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref(true);
const form = useTemplateRef("form");

const { updatedCollection, updateCollection, saveChanges } = useEditCollection(
  props.collection,
  () => (showDialog.value = false),
  isValid,
);

watch(updatedCollection, () => form.value?.validate());
</script>

<template>
  <Dialog
    v-model:show-dialog="showDialog"
    title="Add Description"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8" />
        <DescriptionInput
          :description="updatedCollection.description"
          @description-changed="
            description =>
              updateCollection({
                ...updatedCollection,
                description,
              })
          "
        />
      </v-form>
    </template>
  </Dialog>
</template>
