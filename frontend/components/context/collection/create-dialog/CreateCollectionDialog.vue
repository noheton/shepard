<script setup lang="ts">
import {
  CollectionApi,
  CollectionTemplateApi,
  PermissionType,
  ShepardTemplateApi,
  type ShepardTemplateIO,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import type { CollectionToCreate } from "./collectionToCreate";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits<{
  (e: "collection-created", value: number): void;
}>();

// ── Template picker ──────────────────────────────────────────────────────────

type Mode = "picker" | "form";
const mode = ref<Mode>("form");
const collectionTemplates = ref<ShepardTemplateIO[]>([]);
const isLoadingTemplates = ref(true);
const selectedTemplate = ref<ShepardTemplateIO | null>(null);

useV2ShepardApi(ShepardTemplateApi)
  .value.getTemplates({ kind: "COLLECTION_RECIPE" })
  .then(templates => {
    collectionTemplates.value = templates.filter(t => !t.retired);
    if (collectionTemplates.value.length > 0) mode.value = "picker";
  })
  .catch(() => {
    /* stay in form mode */
  })
  .finally(() => {
    isLoadingTemplates.value = false;
  });

function onTemplateSelected(template: ShepardTemplateIO) {
  selectedTemplate.value = template;
  if (template.description) {
    collectionToCreate.value.description = template.description;
  }
  mode.value = "form";
}

// ── Blank / template-prefilled form ─────────────────────────────────────────

const collectionToCreate = ref<CollectionToCreate>({
  name: "",
  description: "",
  attributes: {},
  // LIC1 (FAIR-1): default both license and accessRights to null
  // (= "not yet declared"). User can pick / leave blank in step 2.
  license: null,
  accessRights: null,
});
const permissionType = ref<PermissionType>(PermissionType.Private);

const isValid = ref(true);
const form = useTemplateRef("form");
watch(collectionToCreate, () => form.value?.validate(), { deep: true });

async function saveChanges() {
  if (isValid.value === false) return;
  const collectionApi = useShepardApi(CollectionApi);

  const created = await collectionApi.value
    .createCollection({ collection: collectionToCreate.value })
    .catch(error => {
      handleError(error, "updateCollection");
      return undefined;
    });
  if (!created) return;

  const collectionId = created.id;

  const currentPermissions = await collectionApi.value
    .getCollectionPermissions({ collectionId })
    .catch(error => {
      handleError(error, "getPermissions");
      return undefined;
    });
  if (!currentPermissions) return;

  const permissionsUpdateSuccess = await collectionApi.value
    .editCollectionPermissions({
      collectionId,
      permissions: { ...currentPermissions, permissionType: permissionType.value },
    })
    .then(() => true)
    .catch(error => {
      handleError(error, "updatePermissions");
      return false;
    });
  if (!permissionsUpdateSuccess) return;

  if (selectedTemplate.value) {
    const collectionAppId = (created as unknown as { appId?: string | null }).appId;
    if (collectionAppId) {
      await useV2ShepardApi(CollectionTemplateApi)
        .value.recordTemplateUsage({
          collectionAppId,
          templateAppId: selectedTemplate.value.appId,
        })
        .catch(() => {
          /* non-critical — don't block navigation */
        });
    }
  }

  emitSuccess(`Successfully created collection "${collectionToCreate.value.name}"`);
  emit("collection-created", collectionId);
  showDialog.value = false;
}
</script>

<template>
  <!-- Template picker: shown when COLLECTION_RECIPE templates exist -->
  <TemplatePickerDialog
    v-if="mode === 'picker'"
    v-model:show-dialog="showDialog"
    title="Create Collection"
    :templates="collectionTemplates"
    :loading="isLoadingTemplates"
    @select="onTemplateSelected"
    @start-blank="mode = 'form'"
  />

  <!-- Form: shown after template pick (pre-filled) or "Start from blank" -->
  <v-form v-else-if="mode === 'form'" ref="form" v-model="isValid">
    <StepperDialog
      v-model:show-dialog="showDialog"
      title="Create Collection"
      :steps="['Collection Properties', 'Additional Information']"
      :submit-disabled="!isValid"
      @submit="saveChanges"
    >
      <template #form-content-step-1>
        <v-row>
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Collection Properties</div>
          </v-col>
        </v-row>
        <v-row class="mt-1">
          <v-col class="pb-0">
            <NameInput v-model:name="collectionToCreate.name" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <DescriptionInput
              v-model:description="collectionToCreate.description"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-2">
            <PermissionTypeInput v-model:permission-type="permissionType" />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
      </template>
      <template #form-content-step-2>
        <v-row>
          <v-col class="pt-9 pb-1">
            <div class="text-subtitle-1">Additional Information</div>
          </v-col>
        </v-row>
        <!-- LIC1 (FAIR-1): license + accessRights. Step 2 (Additional
             Information) is the right home — required for FAIR funder review
             but not blocking creation, so users can leave blank and fill in
             later via the edit dialog. -->
        <v-row class="mt-1">
          <v-col cols="12" md="6" class="pb-0">
            <LicenseInput v-model:license="collectionToCreate.license" />
          </v-col>
          <v-col cols="12" md="6" class="pb-0">
            <AccessRightsInput v-model:access-rights="collectionToCreate.accessRights" />
          </v-col>
        </v-row>
        <v-row class="mt-1">
          <v-col>
            <AttributesInput
              v-model:attributes="collectionToCreate.attributes"
            />
          </v-col>
        </v-row>
      </template>
    </StepperDialog>
  </v-form>
</template>
