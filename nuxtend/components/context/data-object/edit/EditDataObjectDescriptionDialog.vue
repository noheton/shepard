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
  <Dialog
    v-model:show-dialog="showDialog"
    title="Edit Description"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" ref="form" v-model="isValid">
        <v-row class="pt-8" />
        <DescriptionInput v-model:description="updatedDataObject.description" />
      </v-form>
    </template>
  </Dialog>
</template>
