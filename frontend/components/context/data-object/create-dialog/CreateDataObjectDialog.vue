<script setup lang="ts">
import {
  CollectionTemplateApi,
  DataObjectApi,
  type ShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";
import type { DataObjectToCreate } from "./dataObjectToCreate";

interface CreateDataObjectDialogProps {
  collectionId: number;
  collectionAppId?: string;
  parentId?: number;
}
const props = defineProps<CreateDataObjectDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits(["data-object-created"]);

const router = useRouter();

// ── Template picker ──────────────────────────────────────────────────────────

const { advancedMode } = useAdvancedMode();

type Mode = "picker" | "form";
const mode = ref<Mode>("form");
const allowedTemplates = ref<ShepardTemplateIO[]>([]);
const isLoadingTemplates = ref(false);
const isInstantiating = ref(false);

if (props.collectionAppId) {
  isLoadingTemplates.value = true;
  mode.value = "picker";
  useV2ShepardApi(CollectionTemplateApi)
    .value.listAllowedTemplates({ collectionAppId: props.collectionAppId })
    .then(templates => {
      allowedTemplates.value = templates;
      if (templates.length === 0) mode.value = "form";
    })
    .catch(() => {
      mode.value = "form";
    })
    .finally(() => {
      isLoadingTemplates.value = false;
    });
}

async function onTemplateSelected(template: ShepardTemplateIO) {
  if (!props.collectionAppId) return;
  isInstantiating.value = true;
  try {
    const created = await useV2ShepardApi(CollectionTemplateApi)
      .value.instantiateDataObject({
        collectionAppId: props.collectionAppId,
        templateAppId: template.appId,
      });
    emitSuccess(`Created "${created.name}" from template "${template.name}"`);
    emit("data-object-created");
    router.push(
      collectionsPath + props.collectionId + dataObjectsPathFragment + created.id,
    );
    showDialog.value = false;
  } catch (error) {
    handleError(error, "instantiateDataObject");
  } finally {
    isInstantiating.value = false;
  }
}

// ── Blank form ───────────────────────────────────────────────────────────────

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

  useShepardApi(DataObjectApi)
    .value.createDataObject({
      collectionId: props.collectionId,
      dataObject: {
        ...dataObjectToSave,
        predecessorIds: uniqueNumbersOf(
          dataObjectToSave.predecessorIds?.filter(entry => entry != -1) ?? [],
        ),
      },
    })
    .then(response => {
      emitSuccess(`Successfully created data object "${dataObjectToSave.name}"`);
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
  <!-- Template picker: default when allowed templates exist -->
  <TemplatePickerDialog
    v-if="mode === 'picker'"
    v-model:show-dialog="showDialog"
    title="Create Data Object"
    :templates="allowedTemplates"
    :loading="isLoadingTemplates"
    :is-instantiating="isInstantiating"
    :de-emphasise-blank="!advancedMode && allowedTemplates.length > 0"
    @select="onTemplateSelected"
    @start-blank="mode = 'form'"
  />

  <!-- Blank form: fallback or after "Start from blank" -->
  <v-form v-else-if="mode === 'form'" ref="form" v-model="isValid">
    <StepperDialog
      v-model:show-dialog="showDialog"
      title="Create Data Object"
      :steps="['Properties / Relationships', 'Attributes']"
      :submit-disabled="!isValid"
      @submit="createDataObject"
    >
      <template #form-content-step-1>
        <v-row>
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Properties</div>
          </v-col>
        </v-row>
        <v-row class="mt-1">
          <v-col class="pb-0">
            <NameInput v-model:name="dataObjectToCreate.name" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <DescriptionInput
              v-model:description="dataObjectToCreate.description"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Relationships</div>
          </v-col>
        </v-row>
        <v-row class="mt-1">
          <v-col class="pb-2">
            <ParentInput
              v-model:parent-id="dataObjectToCreate.parentId"
              :collection-id="collectionId"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-2">
            <PredecessorInput
              v-model:predecessor-ids="dataObjectToCreate.predecessorIds"
              :collection-id="collectionId"
            />
          </v-col>
        </v-row>
      </template>
      <template #form-content-step-2>
        <v-row>
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Attributes</div>
          </v-col>
        </v-row>
        <v-row class="mt-1">
          <v-col cols="12">
            <AttributesInput
              v-model:attributes="dataObjectToCreate.attributes"
            />
          </v-col>
        </v-row>
      </template>
    </StepperDialog>
  </v-form>
</template>
