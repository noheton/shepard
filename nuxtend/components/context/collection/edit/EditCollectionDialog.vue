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
const { updatedCollection, updatedPermissions, saveChanges } =
  useEditCollection(
    props.collection,
    () => (showDialog.value = false),
    isValid,
    props.isAllowedToEditPermissions,
  );

const form = useTemplateRef("form");
watch(updatedCollection, () => form.value?.validate(), { deep: true });
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
        <NameInput v-model:name="updatedCollection.name" />
        <DescriptionInput v-model:description="updatedCollection.description" />
        <CollectionPermissionsInput
          v-if="isAllowedToEditPermissions"
          v-model:permissions="updatedPermissions"
          :collection-id="collection.id"
        />
        <MandatoryFieldHint />
      </v-form>
    </template>
  </Dialog>
</template>
