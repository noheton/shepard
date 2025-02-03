<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";
import type { DataObjectToCreate } from "./dataObjectToCreate";

interface DataObjectCreateDialogWrapperProps {
  collectionId: number;
  parentId?: number;
  title: string;
}

const router = useRouter();

const props = defineProps<DataObjectCreateDialogWrapperProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-created"]);

const dataObjectToCreate = ref<DataObjectToCreate>({
  description: "",
  name: "",
  parentId: props.parentId ?? null,
  attributes: {},
  predecessorIds: [],
});

const form = useTemplateRef("form");
function updateDataObjectToCreate(newValue: DataObjectToCreate) {
  dataObjectToCreate.value = newValue;
  form.value?.validate();
}

const isValid = ref<boolean>(true);

async function createDataObject() {
  const dataObjectToSave = dataObjectToCreate.value;
  if (dataObjectToSave === undefined) return;
  if (isValid.value === false) return;

  createApiInstance(DataObjectApi)
    .createDataObject({
      collectionId: props.collectionId,
      dataObject: {
        ...dataObjectToSave,
        predecessorIds: uniqueNumbersOf(
          // clean up possible remaining placeholder entries
          dataObjectToSave.predecessorIds?.filter(entry => entry != -1) ?? [],
        ),
      },
    })
    .then(response => {
      emitSuccess(`Successfully updated data object ${dataObjectToSave.name}`);
      emit("data-object-created");
      router.push(
        collectionsPath +
          props.collectionId +
          dataObjectsPathFragment +
          response.id,
      );
      showDialog.value = false;
    })
    .catch(error => {
      handleError(error, "createDataObject");
    });
}
</script>

<template>
  <CommonDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :title="title"
    :submit-disabled="!isValid"
    @submit="createDataObject"
  >
    <template #form>
      <v-form v-if="!!dataObjectToCreate" ref="form" v-model="isValid">
        <slot
          name="inputs"
          :collection-id="collectionId"
          :updated-data-object="dataObjectToCreate"
          :update-data-object="updateDataObjectToCreate"
        />
      </v-form>
    </template>
  </CommonDialog>
</template>
