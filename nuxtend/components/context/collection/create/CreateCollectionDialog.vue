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
    `Successfully created collection ${collectionToCreate.value.name}`,
  );
  emit("collection-created", collectionId);
  showDialog.value = false;
}
</script>

<template>
  <StepperDialog
    v-model:show-dialog="showDialog"
    title="Create Collection"
    :steps="['Collection Properties', 'Additional Information']"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form-content-step-1>
      <v-form ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6">Collection Properties</div>
        <v-row class="pt-4" />
        <NameInput v-model:name="collectionToCreate.name" />
        <DescriptionInput
          v-model:description="collectionToCreate.description"
        />
        <PermissionTypeInput v-model:permission-type="permissionType" />
        <MandatoryFieldHint />
      </v-form>
    </template>
    <template #form-content-step-2>
      <v-form ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6 pb-3">Additional Information</div>
        <AttributesInput v-model:attributes="collectionToCreate.attributes" />
      </v-form>
    </template>
  </StepperDialog>
</template>
