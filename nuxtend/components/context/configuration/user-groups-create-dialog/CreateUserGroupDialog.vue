<script setup lang="ts">
import {
  PermissionType,
  SearchApi,
  UserGroupApi,
} from "@dlr-shepard/backend-client";

const isValid = ref(true);
const userGroupTitle = ref<string>("");
const permissionType = ref<PermissionType>(PermissionType.Private);

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const titleError = ref<boolean>(false);

const emit = defineEmits<{
  (e: "user-group-created", userGroupId: number): void;
}>();

const userGroupSearchStringParam = (name: string) =>
  JSON.stringify({
    property: "name",
    value: name,
    operator: "eq",
  });

async function createUserGroup() {
  await createApiInstance(SearchApi)
    .searchUserGroups({
      userSearchBody: {
        searchParams: {
          query: userGroupSearchStringParam(userGroupTitle.value),
        },
      },
    })
    .then(searchResults => {
      if (searchResults.results && searchResults.results.length > 0) {
        titleError.value = true;
        return;
      }
      createApiInstance(UserGroupApi)
        .createUserGroup({
          userGroup: {
            name: userGroupTitle.value,
            usernames: [],
          },
        })
        .then(response => {
          const createdUserGroup = response;
          createApiInstance(UserGroupApi)
            .editUserGroupPermissions({
              userGroupId: createdUserGroup.id,
              permissions: {
                permissionType: permissionType.value,
                reader: [],
                writer: [],
                manager: [],
              },
            })
            .then(_ => {
              emitSuccess(
                `Successfully created user group "${createdUserGroup.name}"`,
              );
              emit("user-group-created", createdUserGroup.id);
              showDialog.value = false;
            });
        })
        .catch(error => {
          handleError(error, "createUserGroup");
        });
    });
}

const validationRules = [
  (value: unknown) => {
    if (value) return true;
    return `Title is required.`;
  },
];
</script>
<template>
  <FormDialog
    v-model:show-dialog="showDialog"
    :max-width="800"
    title="Create User Group"
    :submit-disabled="!isValid"
    save-button-text="Add"
    @submit="createUserGroup"
  >
    <template #form>
      <v-form ref="form" v-model="isValid">
        <v-row class="pt-9 pb-1">
          <v-col>
            <v-text-field
              v-model:model-value="userGroupTitle"
              :rules="validationRules"
              label="Title*"
              variant="outlined"
              density="compact"
              require
              color="primary"
              :error="titleError"
              hide-details
              @update:model-value="
                () => {
                  if (titleError === true) {
                    titleError = false;
                  }
                }
              "
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <PermissionTypeInput v-model:permission-type="permissionType" />
          </v-col>
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
