<script setup lang="ts">
import { CollectionApi, PermissionType } from "@dlr-shepard/backend-client";
import type { CollectionToCreate } from "./collectionToCreate";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const collectionToCreate = ref<CollectionToCreate>({
  name: "",
  description: "",
  attributes: {},
});

const emit = defineEmits<{
  (e: "collection-created", value: number): void;
}>();

const form = useTemplateRef("form");
function updateCollectionToCreate(newValue: CollectionToCreate) {
  collectionToCreate.value = newValue;
  form.value?.validate();
}

const permissionType = ref<PermissionType>(PermissionType.Private);

function updatePermissionType(newValue: PermissionType) {
  permissionType.value = newValue;
}

const isValid = ref(true);

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
    <template #step1-form>
      <v-form ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6">Collection Properties</div>
        <v-row class="pt-4" />
        <NameInput
          :name="collectionToCreate.name"
          @name-changed="
            name => updateCollectionToCreate({ ...collectionToCreate, name })
          "
        />
        <DescriptionInput
          :description="collectionToCreate.description"
          @description-changed="
            description =>
              updateCollectionToCreate({ ...collectionToCreate, description })
          "
        />
        <PermissionTypeInput
          :permission-type="permissionType"
          :update-permission-type="updatePermissionType"
        />
        <MandatoryFieldHint />
      </v-form>
    </template>
    <template #step2-form>
      <v-form ref="form" v-model="isValid">
        <div class="text-subtitle-2 pt-6 pb-3">Additional Information</div>
        <AttributesInput
          :attributes="collectionToCreate.attributes"
          @attributes-changed="
            attributes =>
              updateCollectionToCreate({
                ...collectionToCreate,
                attributes,
              })
          "
        />
      </v-form>
    </template>
  </StepperDialog>
</template>
