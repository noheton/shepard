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
const { updatedCollection, saveChanges } = useEditCollection(
  props.collection,
  () => (showDialog.value = false),
  isValid,
);

const form = useTemplateRef("form");
watch(updatedCollection, () => form.value?.validate(), { deep: true });
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Add / Edit Attributes"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8">
          <v-col>
            <AttributesInput
              v-model:attributes="updatedCollection.attributes"
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
