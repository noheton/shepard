<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";
import type { UpdatedDataObject } from "./updatedDataObject";

interface DataObjectEditDialogProps {
  collectionId: number;
  dataObjectId: number;
  parentId: number | undefined;
  title: string;
}

const props = defineProps<DataObjectEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-updated"]);

const { dataObject } = useFetchDataObject(
  props.collectionId,
  props.dataObjectId,
);
const updatedDataObject = ref<UpdatedDataObject | undefined>(undefined);
function updateDataObject(newValue: UpdatedDataObject) {
  updatedDataObject.value = newValue;
}

const isValid = ref<boolean>(true);

watch(dataObject, newDo => {
  if (newDo) {
    updateDataObject({
      name: newDo.name,
      parentId: newDo.parentId,
      attributes: newDo.attributes,
      description: newDo.description,
      predecessorIds: newDo.predecessorIds,
    });
  }
});

async function saveChanges() {
  const dataObjectToSave = updatedDataObject.value;
  if (dataObjectToSave === undefined) return;
  if (isValid.value === false) return;

  createApiInstance(DataObjectApi)
    .updateDataObject({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      dataObject: {
        ...dataObjectToSave,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          dataObjectToSave.predecessorIds?.filter(entry => entry != -1) ?? [],
        ),
      },
    })
    .then(_ => {
      emitSuccess(`Successfully updated data object ${dataObjectToSave.name}`);
      emit("data-object-updated");
      handleDataObjectUpdate();
      showDialog.value = false;
    })
    .catch(error => {
      handleError(error, "updateDataObject");
    });
}
</script>

<template>
  <EntityDialog
    v-model:show-dialog="showDialog"
    :title="title"
    :loading="!dataObject && !updatedDataObject"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-if="!!updatedDataObject" v-model="isValid">
        <slot
          name="inputs"
          :collection-id="collectionId"
          :updated-data-object="updatedDataObject"
          :update-data-object="updateDataObject"
        />
      </v-form>
    </template>
  </EntityDialog>
</template>
