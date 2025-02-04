<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";
import type { DataObjectToCreate } from "./dataObjectToCreate";

interface CreateDataObjectDialogProps {
  collectionId: number;
  parentId?: number;
}
const props = defineProps<CreateDataObjectDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-created"]);

const router = useRouter();

const dataObjectToCreate = ref<DataObjectToCreate>({
  description: "",
  name: "",
  parentId: props.parentId ?? null,
  attributes: {},
  predecessorIds: [],
});

const isValid = ref<boolean>(true);
const form = useTemplateRef("form");
watch(dataObjectToCreate, () => form.value?.validate(), { deep: true });

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
  <StepperDialog
    v-model:show-dialog="showDialog"
    title="Create Data Object"
    :steps="['Properties / Relationships', 'Attributes']"
    :submit-disabled="!isValid"
    @submit="createDataObject"
  >
    <template #form-content-step-1>
      <v-form v-if="!!dataObjectToCreate" ref="form" v-model="isValid">
        <v-row class="pt-8" />
        <v-row>
          <v-col>
            <div class="text-subtitle-2">Properties</div>
          </v-col>
        </v-row>
        <NameInput v-model:name="dataObjectToCreate.name" />
        <DescriptionInput
          v-model:description="dataObjectToCreate.description"
        />
        <MandatoryFieldHint />
        <v-row class="pt-8">
          <v-col>
            <div class="text-subtitle-2">Relationships</div>
          </v-col>
        </v-row>
        <ParentInput
          v-model:parent-id="dataObjectToCreate.parentId"
          :collection-id="collectionId"
        />
        <PredecessorInput
          v-model:predecessor-ids="dataObjectToCreate.predecessorIds"
          :collection-id="collectionId"
        />
      </v-form>
    </template>
    <template #form-content-step-2>
      <v-form v-if="!!dataObjectToCreate" ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6 pb-3">Attributes</div>
        <AttributesInput v-model:attributes="dataObjectToCreate.attributes" />
      </v-form>
    </template>
  </StepperDialog>
</template>
