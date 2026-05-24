<script setup lang="ts">
import type { Collection } from "@dlr-shepard/backend-client";
import { useEditCollection } from "./useEditCollection";
import { useAdvancedMode } from "~/composables/context/useAdvancedMode";

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

const { advancedMode } = useAdvancedMode();

const form = useTemplateRef("form");
watch(updatedCollection, () => form.value?.validate(), { deep: true });
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :title="`Edit &quot;${collection.name}&quot;`"
    :loading="isAllowedToEditPermissions && !updatedPermissions"
    :submit-disabled="!isValid"
    @submit="saveChanges"
  >
    <template #form>
      <v-form ref="form" v-model="isValid" validate-on="invalid-input eager">
        <v-row class="pt-8">
          <v-col class="pb-0">
            <NameInput v-model:name="updatedCollection.name" />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <DescriptionInput
              v-model:description="updatedCollection.description"
            />
          </v-col>
        </v-row>
        <!-- LIC1 (FAIR-1): license + accessRights. Visible in BOTH basic and
             advanced mode — these are FAIR-mandatory fields for funder
             review (DFG, EU Horizon Europe, Clean Aviation JU). Hiding them
             in basic mode would defeat their purpose. -->
        <v-row>
          <v-col cols="12" md="6" class="pb-0">
            <LicenseInput v-model:license="updatedCollection.license" />
          </v-col>
          <v-col cols="12" md="6" class="pb-0">
            <AccessRightsInput v-model:access-rights="updatedCollection.accessRights" />
          </v-col>
        </v-row>
        <!-- Hero image URL — advanced mode only (not shown in basic mode).
             Advanced mode is a strict superset: this field is not shown in
             basic mode, but when the user enables advanced mode they see
             everything basic shows plus this field. -->
        <v-row v-if="advancedMode">
          <v-col>
            <v-text-field
              v-model="updatedCollection.heroImageUrl"
              label="Hero image URL"
              hint="Optional wide banner displayed above the Collection title. Enter a public image URL (JPEG, PNG, …). Leave blank to show no banner."
              persistent-hint
              clearable
              density="compact"
              prepend-inner-icon="mdi-image-outline"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-2">
            <CollectionPermissionsInput
              v-if="isAllowedToEditPermissions"
              v-model:permissions="updatedPermissions"
              :collection-id="collection.id"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col class="pt-1">
            <MandatoryFieldHint />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
