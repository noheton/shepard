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

function updateCollectionToCreate(newValue: CollectionToCreate) {
  collectionToCreate.value = newValue;
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
  <EntityDialog
    v-model:show-dialog="showDialog"
    title="Create Collection"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form v-model="isValid">
        <slot
          name="inputs"
          :collection="collectionToCreate"
          :permission-type="permissionType"
          :update-collection="updateCollectionToCreate"
          :update-permission-type="updatePermissionType"
        />
      </v-form>
    </template>
  </EntityDialog>
</template>
