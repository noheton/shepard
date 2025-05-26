<script setup lang="ts">
import { PermissionType, type UserGroup } from "@dlr-shepard/backend-client";
import { createUserGroup } from "~/composables/context/useCreateUserGroup";

const isValid = ref(true);
const userGroupTitle = ref<string>("");
const permissionType = ref<PermissionType>(PermissionType.Private);

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const titleError = ref<boolean>(false);

const emit = defineEmits<{
  (e: "user-group-created", userGroupId: UserGroup): void;
}>();

const validationRules = [
  (value: unknown) => {
    if (value) return true;
    return `Title is required.`;
  },
];

async function onSubmit() {
  await createUserGroup(userGroupTitle.value, permissionType.value).then(
    response => {
      if (!response) {
        titleError.value = true;
        return;
      }
      emit("user-group-created", response);
      showDialog.value = false;
    },
  );
}

function updateErrorState() {
  if (titleError.value === true) {
    titleError.value = false;
  }
}
</script>
<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :max-width="800"
    title="Create User Group"
    :submit-disabled="!isValid"
    save-button-text="Add"
    @submit="onSubmit"
  >
    <template #form>
      <v-form ref="form" v-model="isValid">
        <v-row class="pt-9 pb-1">
          <v-col>
            <v-text-field
              v-model:model-value="userGroupTitle"
              :rules="validationRules"
              label="Group Name"
              variant="outlined"
              density="compact"
              require
              color="primary"
              :error="titleError"
              hide-details
              @update:model-value="updateErrorState"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <PermissionTypeInput
              v-model:permission-type="permissionType"
              :limited-permission-set="[
                PermissionType.Private,
                PermissionType.PublicReadable,
              ]"
              no-required-hint
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col><MandatoryFieldHint /></v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
