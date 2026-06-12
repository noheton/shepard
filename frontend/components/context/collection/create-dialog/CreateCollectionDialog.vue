<script setup lang="ts">
import {
  CollectionApi,
  CollectionTemplatesApi,
  PermissionType,
  TemplatesApi,
  type Collection,
  type ResponseError,
  type ShepardTemplate,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import type { CollectionToCreate } from "./collectionToCreate";

/**
 * BUG-COLL-APPID-ROUTE-005 (2026-06-02): Collection CREATE routes through
 * `POST /v2/collections`. The generated v1 `createCollection` still works
 * but returns a Collection whose `appId` may be unstamped on legacy
 * deploys; using v2 guarantees both `id` and `appId` are present in the
 * response so the post-create permissions flow (still on v1 per PERMS-1
 * hold-back) can look up by `id` reliably.
 */
function v2BaseUrl(): string {
  const config = useRuntimeConfig().public;
  const explicit = config.backendV2ApiUrl as string | undefined;
  if (explicit && explicit.length > 0) return explicit.replace(/\/$/, "");
  return (config.backendApiUrl as string)
    .replace(/\/shepard\/api\/?$/, "")
    .replace(/\/$/, "");
}

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits<{
  // value is the new Collection's appId when available (preferred for v2 routes),
  // falling back to the stringified numeric id when appId is missing.
  (e: "collection-created", value: string): void;
}>();

// ── Template picker ──────────────────────────────────────────────────────────

type Mode = "picker" | "form";
const mode = ref<Mode>("form");
const collectionTemplates = ref<ShepardTemplate[]>([]);
const isLoadingTemplates = ref(true);
const selectedTemplate = ref<ShepardTemplate | null>(null);

useV2ShepardApi(TemplatesApi)
  .value.listTemplates({ kind: "COLLECTION_RECIPE" })
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

function onTemplateSelected(template: ShepardTemplate) {
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

  // BUG-COLL-APPID-ROUTE-005: CREATE via v2. Permissions edit + lookup
  // below stay on v1 (PERMS-1 hold-back).
  const { data: session } = useAuth();
  const accessToken = session.value?.accessToken;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  const created = await fetch(`${v2BaseUrl()}/v2/collections`, {
    method: "POST",
    headers,
    body: JSON.stringify(collectionToCreate.value),
  })
    .then(async resp => {
      if (!resp.ok) {
        throw {
          response: resp,
          message: `HTTP ${resp.status}`,
        } as unknown as ResponseError;
      }
      return (await resp.json()) as Collection;
    })
    .catch(error => {
      handleError(error as ResponseError, "createCollection");
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
      // V2-SWEEP-001-CLIENT-REGEN: recordTemplateUsage was folded into the
      // unified `instantiate` op (POST /v2/collections/{appId}/templates/from/
      // {templateAppId}); the path param is now `appId` (was collectionAppId).
      await useV2ShepardApi(CollectionTemplatesApi)
        .value.instantiate({
          appId: collectionAppId,
          templateAppId: selectedTemplate.value.appId,
        })
        .catch(() => {
          /* non-critical — don't block navigation */
        });
    }
  }

  emitSuccess(`Successfully created collection "${collectionToCreate.value.name}"`);
  const createdAppId = (created as unknown as { appId?: string | null }).appId;
  emit("collection-created", createdAppId ?? String(collectionId));
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
