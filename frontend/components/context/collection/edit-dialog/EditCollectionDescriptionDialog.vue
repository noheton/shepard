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
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Add Description"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8">
          <v-col>
            <DescriptionInput
              v-model:description="updatedCollection.description"
            />
          </v-col>
        </v-row>
        <v-row class="pt-4">
          <v-col>
            <v-select
              v-model="updatedCollection.status"
              label="Status"
              :items="['DRAFT', 'IN_REVIEW', 'READY', 'PUBLISHED', 'ARCHIVED']"
              clearable
              hint="Optional lifecycle status. Leave blank to clear."
              persistent-hint
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
