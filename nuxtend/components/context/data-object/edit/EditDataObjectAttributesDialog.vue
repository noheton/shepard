<script setup lang="ts">
import { useEditDataObject } from "./useEditDataObject";

interface EditDataObjectAttributesDialogProps {
  collectionId: number;
  dataObjectId: number;
}

const props = defineProps<EditDataObjectAttributesDialogProps>();
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
    title="Add / Edit Attributes"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" ref="form" v-model="isValid">
        <v-row class="pt-8" />
        <AttributesInput v-model:attributes="updatedDataObject.attributes" />
      </v-form>
    </template>
  </Dialog>
</template>
