<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useEditCollection } from "./useEditCollection";

interface CollectionEditDialogProps {
  collection: Collection;
  isAllowedToEditPermissions?: boolean;
}

const props = defineProps<CollectionEditDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref(true);
const form = useTemplateRef("form");

const {
  updatedCollection,
  updateCollection,
  updatedPermissions,
  updatePermissions,
  saveChanges,
} = useEditCollection(
  props.collection,
  () => (showDialog.value = false),
  isValid,
  props.isAllowedToEditPermissions,
);

watch(updatedCollection, () => form.value?.validate());
</script>

<template>
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :title="`Edit &quot;${collection.name}&quot;`"
    :loading="isAllowedToEditPermissions && !updatedPermissions"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8" />
        <NameInput
          :name="updatedCollection.name"
          @name-changed="
            name => updateCollection({ ...updatedCollection, name })
          "
        />
        <DescriptionInput
          :description="updatedCollection.description"
          @description-changed="
            description =>
              updateCollection({ ...updatedCollection, description })
          "
        />
        <CollectionPermissionsInput
          v-if="isAllowedToEditPermissions"
          :updated-permissions="updatedPermissions"
          :collection-id="collection.id"
          :update-permissions="updatePermissions"
        />
        <MandatoryFieldHint />
      </v-form>
    </template>
  </Dialog>
</template>
