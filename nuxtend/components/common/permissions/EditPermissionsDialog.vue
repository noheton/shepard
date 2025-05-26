<script lang="ts" setup>
import {
  instanceOfUser,
  instanceOfUserGroup,
  PermissionType,
  type Permissions,
  type ResponseError,
  type User,
} from "@dlr-shepard/backend-client";
import type { Member } from "~/composables/common/permissions/useMemberSearch";
import type { ShepardObjectAccessor } from "~/composables/shepardObjectAccessor";
import {
  handleShepardObjectUpdate,
  onShepardObjectUpdated,
} from "~/utils/resourceUpdateBus";
import { mapMemberPermissions, mapPermissions } from "./mapPermissions";
import { UserRole } from "./UserRole";

const props = defineProps<{ shepardObjectAccessor: ShepardObjectAccessor }>();
const shepardObjectAccessor = props.shepardObjectAccessor;

export interface MemberPermissions {
  member: Member;
  roleList: UserRole[];
}

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

if (!shepardObjectAccessor.permissions.value)
  await shepardObjectAccessor.fetchPermissions();

if (!shepardObjectAccessor.roles.value)
  await shepardObjectAccessor.fetchRoles();

if (!shepardObjectAccessor.owner.value)
  await shepardObjectAccessor.fetchOwner();

onShepardObjectUpdated(() => {
  shepardObjectAccessor.fetchPermissions();
  shepardObjectAccessor.fetchRoles();
  shepardObjectAccessor.fetchOwner();
});

const owner = shepardObjectAccessor.owner;
const isOwner = shepardObjectAccessor.roles.value!.owner;

const shepardObjectPermissions = shepardObjectAccessor.permissions;

const isValid = ref(true);
const isOwnerInputValid = ref(true);

const selectedAdditionalUserRole = ref<UserRole | undefined>(undefined);

const memberPermissionsList = ref<MemberPermissions[] | undefined>(undefined);
const updatedPermissions = ref<Permissions>();
const selectedMember = ref<Member | undefined>(undefined);
const filteredRoles = ref<UserRole[]>([]);

updatedPermissions.value = shepardObjectPermissions.value;
await mapPermissions(updatedPermissions.value, memberPermissionsList);

async function saveChanges() {
  try {
    await shepardObjectAccessor.updatePermissions(updatedPermissions.value!);
    showDialog.value = false;
    handleShepardObjectUpdate();
  } catch (error) {
    handleError(error as ResponseError, "updating permissions");
  }
}

const onOwnerChange = (updatedOwner: User | null) => {
  if (!updatedOwner) {
    isOwnerInputValid.value = false;
  } else if (updatedPermissions.value) {
    updatedPermissions.value.owner = updatedOwner?.username;
    isOwnerInputValid.value = true;
  }
};

const onPermissionTypeChange = (updatedPermissionType: PermissionType) => {
  if (updatedPermissions.value)
    updatedPermissions.value.permissionType = updatedPermissionType;
};

const onAddPermission = () => {
  if (
    !memberPermissionsList.value ||
    !selectedAdditionalUserRole.value ||
    !selectedMember.value
  )
    return;
  const addMemberPermission = (
    selectedMember: Member | undefined,
    findMemberCallback: (member: Member) => boolean,
  ) => {
    if (
      !memberPermissionsList.value ||
      !selectedAdditionalUserRole.value ||
      !selectedMember
    )
      return;

    const memberExists = memberPermissionsList.value.find(member =>
      findMemberCallback(member.member),
    );
    // On adding roles, implied roles are added automatically as well.
    const rolesToAdd = (() => {
      switch (selectedAdditionalUserRole.value) {
        case UserRole.manager: {
          return [UserRole.manager, UserRole.writer, UserRole.reader];
        }
        case UserRole.writer: {
          return [UserRole.writer, UserRole.reader];
        }
        default: {
          return [selectedAdditionalUserRole.value];
        }
      }
    })();
    if (!memberExists) {
      memberPermissionsList.value.unshift({
        member: selectedMember,
        roleList: rolesToAdd,
      });
    } else {
      rolesToAdd.forEach(roleToAdd => {
        const roleExists = memberExists.roleList.some(
          role => role === roleToAdd,
        );
        if (!roleExists) memberExists.roleList.push(roleToAdd);
      });
    }
  };
  const memberSelected = selectedMember.value;
  let searchCallback;
  if (instanceOfUser(memberSelected)) {
    searchCallback = (member: Member) =>
      instanceOfUser(member) && member.username === memberSelected.username;
  } else {
    searchCallback = (member: Member) =>
      instanceOfUserGroup(member) && member.id === memberSelected.id;
  }
  addMemberPermission(memberSelected, searchCallback);
  resetAdditionalPermission();
};

const resetAdditionalPermission = () => {
  selectedMember.value = undefined;
  selectedAdditionalUserRole.value = undefined;
};

const onSubmit = () => {
  if (!memberPermissionsList.value || !updatedPermissions.value) return;

  updatedPermissions.value = {
    ...updatedPermissions.value,
    ...mapMemberPermissions(memberPermissionsList.value),
  };

  saveChanges();
};

watch(selectedMember, newMember => {
  if (newMember && !instanceOfUser(newMember))
    filteredRoles.value = Object.values(UserRole).filter(
      role => role !== UserRole.manager,
    );
  else filteredRoles.value = Object.values(UserRole);
});
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :max-width="950"
    :submit-disabled="!(isValid && isOwnerInputValid)"
    title="Manage Permissions"
    @submit="onSubmit"
  >
    <template #form>
      <v-form
        v-if="shepardObjectPermissions"
        ref="form"
        v-model="isValid"
        validate-on="invalid-input eager"
      >
        <v-row class="pt-8">
          <v-col class="text-semibold text-textbody1">
            General Permissions
            <Tooltip>
              <div>Public: Container is available for all.</div>
              <div>Public Readable: Container is readable for all.</div>
              <div>Private: Container has private permissions.</div>
            </Tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <v-select
              v-model="shepardObjectPermissions.permissionType"
              :items="Object.values(PermissionType)"
              density="compact"
              label="Permission Type"
              variant="outlined"
              @update:model-value="onPermissionTypeChange"
            />
          </v-col>
          <v-col>
            <OwnerAutocompleteInput
              v-if="owner"
              :is-owner="isOwner"
              :model-value="owner"
              @owner-change="onOwnerChange"
            />
          </v-col>
        </v-row>
        <v-divider
          class="text-divider1 mb-6 mt-2"
          opacity="100"
          thickness="1px"
        />
        <v-row>
          <v-col class="text-semibold text-textbody1">
            Additional Permissions
            <Tooltip>
              <div>Reader: User can only read contents.</div>
              <div>Writer: User can read and change contents.</div>
              <div>Manager: User can give and change permissions.</div>
              <div>Owner: User has all rights.</div>
            </Tooltip>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <MemberAutocomplete
              :model-value="selectedMember"
              label="User or group id"
              @member-select="
                (selectedAdditionalMember: Member) => {
                  selectedMember = selectedAdditionalMember;
                }
              "
            />
          </v-col>
          <v-col>
            <v-select
              v-model="selectedAdditionalUserRole"
              :items="filteredRoles"
              color="primary"
              density="compact"
              hide-details
              label="User or group role"
              require
              variant="outlined"
            />
          </v-col>
          <v-col class="d-flex align-center" style="max-width: fit-content">
            <v-btn
              color="primary"
              height="40px"
              prepend-icon="mdi-plus-circle"
              variant="flat"
              @click="onAddPermission"
            >
              ADD
            </v-btn>
          </v-col>
        </v-row>
        <v-row class="pt-3">
          <v-col class="text-semibold text-textbody1">
            List of Additional Permissions
          </v-col>
        </v-row>
        <v-row>
          <MemberPermissionList
            v-if="memberPermissionsList"
            v-model:member-permissions="memberPermissionsList"
          />
        </v-row>
      </v-form>
    </template>
  </FormDialog>
</template>
