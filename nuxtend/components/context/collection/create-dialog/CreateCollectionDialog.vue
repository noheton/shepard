<script setup lang="ts">
import { CollectionApi, PermissionType } from "@dlr-shepard/backend-client";
import type { CollectionToCreate } from "./collectionToCreate";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const emit = defineEmits<{
  (e: "collection-created", value: number): void;
}>();

const collectionToCreate = ref<CollectionToCreate>({
  name: "",
  description: "",
  attributes: {},
});
const permissionType = ref<PermissionType>(PermissionType.Private);

const isValid = ref(true);
const form = useTemplateRef("form");
watch(collectionToCreate, () => form.value?.validate(), { deep: true });

async function saveChanges() {
  if (isValid.value === false) return;

  const collectionId = await createApiInstance(CollectionApi)
    .createCollection({
      collection: collectionToCreate.value,
    })
    .then(response => {
      return response.id;
    })
    .catch(error => {
      handleError(error, "updateCollection");
      return undefined;
    });
  if (!collectionId) return;

  const currentPermissions = await createApiInstance(CollectionApi)
    .getCollectionPermissions({ collectionId })
    .catch(error => {
      handleError(error, "getPermissions");
      return undefined;
    });

  if (!currentPermissions) return;

  const permissionsUpdateSuccess = await createApiInstance(CollectionApi)
    .editCollectionPermissions({
      collectionId: collectionId,
      permissions: {
        ...currentPermissions,
        permissionType: permissionType.value,
      },
    })
    .then(_ => {
      return true;
    })
    .catch(error => {
      handleError(error, "updatePermissions");
      return false;
    });
  if (!permissionsUpdateSuccess) return;

  emitSuccess(
    `Successfully created collection "${collectionToCreate.value.name}"`,
  );
  emit("collection-created", collectionId);
  showDialog.value = false;
}
</script>

<template>
  <v-form ref="form" v-model="isValid">
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
        <v-row>
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
