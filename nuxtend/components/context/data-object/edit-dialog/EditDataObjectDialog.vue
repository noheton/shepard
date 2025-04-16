<script setup lang="ts">
import { useEditDataObject } from "./useEditDataObject";

interface EditDataObjectDialogProps {
  collectionId: number;
  dataObjectId: number;
  dataObjectName: string;
}

const props = defineProps<EditDataObjectDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-updated"]);

const isValid = ref<boolean>(true);
const { saveChanges, updatedDataObject, loading } = useEditDataObject(
  props.collectionId,
  props.dataObjectId,
  isValid,
  () => {
    emit("data-object-updated");
    showDialog.value = false;
  },
);

const form = useTemplateRef("form");
watch(updatedDataObject, () => form.value?.validate(), { deep: true });
</script>

<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :title="`Edit &quot;${dataObjectName}&quot;`"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" ref="form" v-model="isValid">
        <v-row class="pt-8">
          <v-col class="pb-2">
            <div class="text-subtitle-1">Properties</div>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-0">
            <NameInput v-model:name="updatedDataObject.name" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <DescriptionInput
              v-model:description="updatedDataObject.description"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-8 pb-2">
            <div class="text-subtitle-1">Relationships</div>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pb-2">
            <ParentInput
              v-model:parent-id="updatedDataObject.parentId"
              :collection-id="collectionId"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-2">
            <PredecessorInput
              v-model:predecessor-ids="updatedDataObject.predecessorIds"
              :collection-id="collectionId"
            />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
