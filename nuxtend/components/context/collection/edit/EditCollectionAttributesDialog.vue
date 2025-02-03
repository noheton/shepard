<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useEditCollection } from "./useEditCollection";

interface CollectionEditAttributesDialogProps {
  collection: Collection;
}

const props = defineProps<CollectionEditAttributesDialogProps>();
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
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Add / Edit Attributes"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8" />
        <AttributesInput
          :attributes="updatedCollection.attributes"
          @attributes-changed="
            attributes =>
              updateCollection({
                ...updatedCollection,
                attributes,
              })
          "
        />
      </v-form>
    </template>
  </Dialog>
</template>
