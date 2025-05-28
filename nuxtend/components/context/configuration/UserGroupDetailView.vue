<script setup lang="ts">
import {
  instanceOfUser,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import {
  SearchType,
  type Member,
} from "~/composables/common/permissions/useMemberSearch";
import {
  useHandleUserGroupMembers,
  UserGroupMemberRole,
  type UserGroupMemberPermissions,
} from "~/composables/context/configuration/useHandleUserGroupMembers";

const { userGroup } = defineProps<{ userGroup: UserGroup }>();
const emit = defineEmits(["back"]);

const showUserRemoveConfirmDialog = ref<boolean>(false);
const userToRemove = ref<User | undefined>(undefined);

const showOwnerChangeConfirmDialog = ref<boolean>(false);
const userToChangeOwner = ref<User | undefined>(undefined);

const showDeleteGroupConfirmDialog = ref<boolean>(false);

const headers = [
  { title: "Name", key: "name", sortable: true, width: "50%" },
  { title: "Role", key: "roleList", sortable: true, width: "30%" },
  {
    title: "",
    value: "actions",
  },
];

const selectedMember = ref<User | undefined>(undefined);

const {
  userGroupMemberPermissions,
  loading,
  isAllowedToEditPermissions,
  isAllowedToEditOwnership,
  addMember,
  removeMember,
  addManager,
  removeManager,
  changeOwner,
  deleteUserGroup,
} = useHandleUserGroupMembers(userGroup);

const onMemberSelect = async (selectedAdditionalMember: Member) => {
  if (instanceOfUser(selectedAdditionalMember)) {
    selectedMember.value = selectedAdditionalMember;
    await addMember(selectedAdditionalMember);
    selectedMember.value = undefined;
  }
};

async function onRemove() {
  if (!userToRemove.value) return;
  await removeMember(userToRemove.value);
  userToRemove.value = undefined;
}

async function onConfirmDeleteUserGroup() {
  await deleteUserGroup();
  emit("back");
}
</script>

<template>
  <v-btn
    variant="plain"
    color="primary"
    width="fit-content"
    class="text-body-2 pl-0 pb-6"
    @click="emit('back')"
  >
    &lt; Back to User Groups
  </v-btn>
  <ConfigurationPane :title="userGroup.name">
    <template #metadata>
      <MetadataColumn>
        <MetadataTextField label="ID" :text="userGroup.id.toString()" />
      </MetadataColumn>

      <v-col v-if="isAllowedToEditOwnership" class="text-right">
        <v-btn
          class="ml-3"
          color="treeview"
          icon
          rounded="lg"
          size="small"
          variant="flat"
          @click="showDeleteGroupConfirmDialog = true"
        >
          <v-icon>mdi-delete-outline</v-icon>
          <v-tooltip activator="parent" location="top">
            Delete user group
          </v-tooltip>
        </v-btn>
      </v-col>
      <ConfirmSafeDeleteDialog
        v-if="showDeleteGroupConfirmDialog"
        v-model:show-dialog="showDeleteGroupConfirmDialog"
        :target-name="userGroup.name"
        entity-type="user group"
        @confirmed="onConfirmDeleteUserGroup"
      />
    </template>
    <template #table>
      <div v-if="isAllowedToEditPermissions" class="pt-8">
        <div class="text-subtitle-1">Add Users</div>
        <div class="pt-2">
          <MemberAutocomplete
            :model-value="selectedMember"
            label="Search for User Name..."
            :search-type="SearchType.USER"
            @member-select="onMemberSelect"
          >
            <template #item="{ props }">
              <v-list-item v-bind="props" :ripple="false" :active="false">
                <template #append>
                  <v-icon
                    class="ms-2 select-icon"
                    color="primary"
                    icon="mdi-account-plus-outline"
                  />
                </template>
              </v-list-item>
            </template>
          </MemberAutocomplete>
        </div>
      </div>
      <div class="text-subtitle-1 pt-12">Members</div>
      <div>
        <DataTable
          class="pt-2"
          :cell-props="{
            class: 'text-textbody1',
          }"
          :header-props="{
            class: 'text-subtitle-2 text-textbody1',
            style: 'background-color: rgb(var(--v-theme-divider2))',
          }"
          :headers="headers"
          :items="userGroupMemberPermissions"
          :loading="loading"
          :items-per-page="-1"
          :hide-default-footer="true"
        >
          <template
            #[`item.name`]="{ item }: { item: UserGroupMemberPermissions }"
          >
            {{ `${item.member.firstName} ${item.member.lastName}` }}
          </template>
          <template
            #[`item.roleList`]="{ item }: { item: UserGroupMemberPermissions }"
          >
            {{
              `${item.roleList.find(role => role === UserGroupMemberRole.owner) ? "Owner" : item.roleList.find(role => role === UserGroupMemberRole.manager) ? "Manager" : ""} `
            }}
          </template>
          <template
            #[`item.actions`]="{ item }: { item: UserGroupMemberPermissions }"
          >
            <ContextMenu
              v-if="
                isAllowedToEditPermissions &&
                !item.roleList.find(role => role === UserGroupMemberRole.owner)
              "
              selector-icon="mdi-dots-vertical"
              :items="[
                ...(isAllowedToEditOwnership
                  ? [
                      {
                        label: 'Make Owner',
                        icon: 'mdi-school-outline',
                        onClick: () => {
                          showOwnerChangeConfirmDialog = true;
                          userToChangeOwner = item.member;
                        },
                      },
                    ]
                  : []),
                ...(item.roleList.find(
                  role => role === UserGroupMemberRole.manager,
                )
                  ? [
                      {
                        label: 'Remove Manager',
                        icon: 'mdi-account-minus-outline',
                        onClick: () => {
                          removeManager(item.member);
                        },
                      },
                    ]
                  : [
                      {
                        label: 'Make Manager',
                        icon: 'mdi-account-plus-outline',
                        onClick: () => {
                          addManager(item.member);
                        },
                      },
                    ]),

                {
                  label: 'Remove from Group',
                  icon: 'mdi-delete-outline',
                  onClick: () => {
                    showUserRemoveConfirmDialog = true;
                    userToRemove = item.member;
                  },
                },
              ]"
            />
          </template>
        </DataTable>
      </div>
    </template>
    <template #confirmation-dialog>
      <ConfirmDeleteDialog
        v-if="showUserRemoveConfirmDialog && userToRemove"
        v-model:show-dialog="showUserRemoveConfirmDialog"
        :prompt-text="`Are you sure you want to remove '${userToRemove.firstName} ${userToRemove.lastName}' from '${userGroup.name}'?`"
        @confirmed="onRemove"
      />
      <OwnerChangeConfirmDialog
        v-if="showOwnerChangeConfirmDialog && userToChangeOwner"
        v-model:show-dialog="showOwnerChangeConfirmDialog"
        @confirmed="changeOwner(userToChangeOwner)"
      />
    </template>
  </ConfigurationPane>
</template>
