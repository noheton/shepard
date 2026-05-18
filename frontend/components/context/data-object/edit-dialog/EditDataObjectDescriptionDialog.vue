<script setup lang="ts">
import { useEditDataObject } from "./useEditDataObject";

interface EditDataObjectDescriptionDialogProps {
  collectionId: number;
  dataObjectId: number;
}

const props = defineProps<EditDataObjectDescriptionDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref<boolean>(true);
const { saveChanges, updatedDataObject, loading } = useEditDataObject(
  props.collectionId,
  props.dataObjectId,
  isValid,
  () => {
    showDialog.value = false;
  },
);

const form = useTemplateRef("form");

watch(updatedDataObject, () => form.value?.validate(), { deep: true });
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    title="Description & Status"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" ref="form" v-model="isValid">
        <v-row class="pt-8">
          <v-col>
            <DescriptionInput
              v-model:description="updatedDataObject.description"
            />
          </v-col>
        </v-row>
        <v-row class="pt-4">
          <v-col>
            <v-select
              v-model="updatedDataObject.status"
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
