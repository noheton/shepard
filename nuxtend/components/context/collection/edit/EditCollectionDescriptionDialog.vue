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
const { updatedCollection, saveChanges } = useEditCollection(
  props.collection,
  () => (showDialog.value = false),
  isValid,
);

const form = useTemplateRef("form");
watch(updatedCollection, () => form.value?.validate(), { deep: true });
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
        <DescriptionInput v-model:description="updatedCollection.description" />
      </v-form>
    </template>
  </Dialog>
</template>
