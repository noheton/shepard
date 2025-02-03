<script setup lang="ts">
import { useEditDataObject } from "./useEditDataObject";

interface DataObjectEditDialogProps {
  collectionId: number;
  dataObjectId: number;
  itemName: string;
}

const props = defineProps<DataObjectEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-updated"]);

const isValid = ref<boolean>(true);
const { saveChanges, updatedDataObject, updateDataObject, loading } =
  useEditDataObject(props.collectionId, props.dataObjectId, isValid, () => {
    emit("data-object-updated");
    showDialog.value = false;
  });

const form = useTemplateRef("form");

watch(updatedDataObject, () => form.value?.validate());
</script>

<template>
  <Dialog
    v-model:show-dialog="showDialog"
    :title="`Edit &quot;${itemName}&quot;`"
    :loading="loading"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" ref="form" v-model="isValid">
        <v-row class="pt-8" />
        <v-row>
          <v-col class="pb-2">
            <div class="text-subtitle-1">Properties</div>
          </v-col>
        </v-row>
        <NameInput
          :name="updatedDataObject.name"
          @name-changed="
            name => {
              if (updatedDataObject)
                updateDataObject({
                  ...updatedDataObject,
                  name,
                });
            }
          "
        />
        <DescriptionInput
          :description="updatedDataObject.description"
          @description-changed="
            description => {
              if (updatedDataObject)
                updateDataObject({ ...updatedDataObject, description });
            }
          "
        />
        <MandatoryFieldHint />
        <v-row>
          <v-col class="pt-8 pb-2">
            <div class="text-subtitle-1">Relationships</div>
          </v-col>
        </v-row>
        <ParentInput
          :collection-id="collectionId"
          :parent-id="updatedDataObject.parentId"
          @parent-changed="
            parentId => {
              if (updatedDataObject)
                updateDataObject({ ...updatedDataObject, parentId });
            }
          "
        />
        <PredecessorInput
          :collection-id="collectionId"
          :predecessor-ids="updatedDataObject.predecessorIds ?? []"
          @predecessors-changed="
            predecessorIds => {
              if (updatedDataObject)
                updateDataObject({ ...updatedDataObject, predecessorIds });
            }
          "
        />
      </v-form>
    </template>
  </Dialog>
</template>
