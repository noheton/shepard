<script setup lang="ts">
import { CollectionApi, type Collection } from "@dlr-shepard/backend-client";
import { handleCollectionUpdate } from "~/utils/resourceUpdateBus";
import type {
  UpdatedCollection,
  UpdatedPermissions,
} from "./collectionEditTypes";

interface CollectionEditDialogProps {
  collection: Collection;
  isAllowedToEditPermissions?: boolean;
  title: string;
}

const props = defineProps<CollectionEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const updatedCollection = ref<UpdatedCollection>({
  name: props.collection.name,
  attributes: props.collection.attributes ?? {},
  description: props.collection.description ?? "",
});

const form = useTemplateRef("form");

function updateCollection(newValue: UpdatedCollection) {
  updatedCollection.value = newValue;
  form.value?.validate();
}

const updatedPermissions = ref<UpdatedPermissions>(undefined);

function updatePermissions(newValue: UpdatedPermissions) {
  updatedPermissions.value = newValue;
}

const isValid = ref(true);

async function saveChanges() {
  if (isValid.value === false) return;

  const collectionUpdateSuccess = await createApiInstance(CollectionApi)
    .updateCollection({
      collectionId: props.collection.id,
      collection: updatedCollection.value,
    })
    .then(_ => {
      return true;
    })
    .catch(error => {
      handleError(error, "updateCollection");
      return false;
    });
  if (!collectionUpdateSuccess) return;

  if (props.isAllowedToEditPermissions && updatedPermissions.value) {
    const permissionsUpdateSuccess = await createApiInstance(CollectionApi)
      .editCollectionPermissions({
        collectionId: props.collection.id,
        permissions: updatedPermissions.value,
      })
      .then(_ => {
        return true;
      })
      .catch(error => {
        handleError(error, "updatePermissions");
        return false;
      });
    if (!permissionsUpdateSuccess) return;
  }

  emitSuccess(
    `Successfully updated collection ${updatedCollection.value.name}`,
  );
  handleCollectionUpdate();
  showDialog.value = false;
}
</script>

<template>
  <EntityDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :title="title"
    :loading="isAllowedToEditPermissions && !updatedPermissions"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <slot
          name="inputs"
          :collection-id="collection.id"
          :updated-collection="updatedCollection"
          :updated-permissions="updatedPermissions"
          :update-collection="updateCollection"
          :update-permissions="updatePermissions"
        />
      </v-form>
    </template>
  </EntityDialog>
</template>
