<script setup lang="ts">
import {
  CollectionTemplatesApi,
  DataObjectsApi,
  type ShepardTemplate,
} from "@dlr-shepard/backend-client";
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
const allowedTemplates = ref<ShepardTemplate[]>([]);
const isLoadingTemplates = ref(false);
const isInstantiating = ref(false);

if (props.collectionAppId) {
  isLoadingTemplates.value = true;
  useV2ShepardApi(CollectionTemplatesApi)
    .value.listAllowed({ appId: props.collectionAppId })
    .then(page => {
      const templates = page.items ?? [];
      allowedTemplates.value = templates;
      // Basic-mode users see the template picker first (templates are the
      // predigested path for users who don't know which containers/annotations
      // to add). Advanced-mode users go straight to the blank form and can
      // opt into a template via the "Use template…" button.
      if (templates.length > 0 && !advancedMode.value) {
        mode.value = "picker";
      }
    })
    .catch(() => {
      // stay on "form" — graceful degradation
    })
    .finally(() => {
      isLoadingTemplates.value = false;
    });
}

async function onTemplateSelected(template: ShepardTemplate) {
  if (!props.collectionAppId) return;
  isInstantiating.value = true;
  try {
    const created = await useV2ShepardApi(CollectionTemplatesApi)
      .value.instantiateDataObject({
        collectionAppId: props.collectionAppId,
        templateAppId: template.appId,
      });
    emitSuccess(`Created "${created.name}" from template "${template.name}"`);
    emit("data-object-created");
    // V2-SWEEP Wave 1: routes carry appIds, never numeric Neo4j ids.
    router.push(
      collectionsPath +
        (props.collectionAppId ?? props.collectionId) +
        dataObjectsPathFragment +
        ((created as unknown as { appId?: string | null }).appId ?? created.id),
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
  // LIC1 (FAIR-1): null = "not yet declared" — user can set in step 2.
  license: null,
  accessRights: null,
});

const isValid = ref<boolean>(true);
const form = useTemplateRef("form");
watch(dataObjectToCreate, () => form.value?.validate(), { deep: true });

async function createDataObject() {
  const dataObjectToSave = dataObjectToCreate.value;
  if (dataObjectToSave === undefined) return;
  if (isValid.value === false) return;
  // SIDEBAR-V2-CREATE: guard; both sidebar call sites always provide collectionAppId.
  if (!props.collectionAppId) return;

  useV2ShepardApi(DataObjectsApi)
    .value.createDataObjectV2({
      collectionAppId: props.collectionAppId,
      createDataObjectV2: {
        ...dataObjectToSave,
        predecessorIds: uniqueNumbersOf(
          dataObjectToSave.predecessorIds?.filter(entry => entry !== -1) ?? [],
        ),
      },
    })
    .then(response => {
      emitSuccess(`Successfully created data object "${dataObjectToSave.name}"`);
      emit("data-object-created");
      router.push(
        collectionsPath +
          props.collectionAppId +
          dataObjectsPathFragment +
          (response.appId ?? response.id),
      );
      showDialog.value = false;
    })
    .catch(error => {
      handleError(error, "createDataObjectV2");
    });
}
</script>

<template>
  <!-- Template picker: default for basic-mode users when templates exist;
       also reachable from the form via "Use template…" (advanced mode). -->
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
        <!-- Advanced-mode: offer template picker as an opt-in shortcut when
             templates are available for this collection. Basic-mode users
             land on the picker first (see script block) and come here only
             after clicking "Skip template / create blank". -->
        <v-row v-if="advancedMode && allowedTemplates.length > 0" class="pt-4 pb-0">
          <v-col class="pb-0">
            <v-btn
              variant="tonal"
              color="primary"
              size="small"
              prepend-icon="mdi-file-document-outline"
              @click="mode = 'picker'"
            >
              Use template&hellip;
            </v-btn>
          </v-col>
        </v-row>
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
            <div class="text-subtitle-1 d-flex align-center ga-1">
              Relationships
              <v-tooltip
                text="Parent organises this data object within a hierarchy. Predecessors link to prior data objects in a process chain — e.g. the test run that a re-test supersedes."
                max-width="320"
                location="top"
              >
                <template #activator="{ props: tip }">
                  <v-icon v-bind="tip" size="16" color="medium-emphasis" icon="mdi-help-circle-outline" />
                </template>
              </v-tooltip>
            </div>
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
            <div class="text-subtitle-1 d-flex align-center ga-1">
              Attributes
              <v-tooltip
                text="Key–value pairs that describe this data object. Use controlled vocabulary keys where possible (e.g. propellant, testStand, operatorId) so data objects can be filtered and compared."
                max-width="320"
                location="top"
              >
                <template #activator="{ props: tip }">
                  <v-icon v-bind="tip" size="16" color="medium-emphasis" icon="mdi-help-circle-outline" />
                </template>
              </v-tooltip>
            </div>
          </v-col>
        </v-row>
        <!-- LIC1 (FAIR-1): license + accessRights. Step 2 alongside the other
             FAIR metadata fields. -->
        <v-row class="mt-1">
          <v-col cols="12" md="6" class="pb-0">
            <LicenseInput v-model:license="dataObjectToCreate.license" />
          </v-col>
          <v-col cols="12" md="6" class="pb-0">
            <AccessRightsInput v-model:access-rights="dataObjectToCreate.accessRights" />
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
