<script setup lang="ts">
import { DataObjectApi } from "@dlr-shepard/backend-client";
import type { DataObjectToCreate } from "./dataObjectToCreate";

interface CreateDataObjectDialogProps {
  collectionId: number;
  parentId?: number;
}

const router = useRouter();

const props = defineProps<CreateDataObjectDialogProps>();
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
        <NameInput
          :name="dataObjectToCreate.name"
          @name-changed="
            name => updateDataObjectToCreate({ ...dataObjectToCreate, name })
          "
        />
        <DescriptionInput
          :description="dataObjectToCreate.description"
          @description-changed="
            description =>
              updateDataObjectToCreate({ ...dataObjectToCreate, description })
          "
        />
        <MandatoryFieldHint />
        <v-row class="pt-8">
          <v-col>
            <div class="text-subtitle-2">Relationships</div>
          </v-col>
        </v-row>
        <ParentInput
          :collection-id="collectionId"
          :parent-id="dataObjectToCreate.parentId"
          @parent-changed="
            parentId =>
              updateDataObjectToCreate({ ...dataObjectToCreate, parentId })
          "
        />
        <PredecessorInput
          :collection-id="collectionId"
          :predecessor-ids="dataObjectToCreate.predecessorIds ?? []"
          @predecessors-changed="
            predecessorIds =>
              updateDataObjectToCreate({
                ...dataObjectToCreate,
                predecessorIds,
              })
          "
        />
      </v-form>
    </template>
    <template #form-content-step-2>
      <v-form v-if="!!dataObjectToCreate" ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6 pb-3">Attributes</div>
        <AttributesInput
          :attributes="dataObjectToCreate.attributes ?? {}"
          @attributes-changed="
            attributes =>
              updateDataObjectToCreate({
                ...dataObjectToCreate,
                attributes,
              })
          "
        />
      </v-form>
    </template>
  </StepperDialog>
</template>
