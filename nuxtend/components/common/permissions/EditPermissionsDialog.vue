<script setup lang="ts">
import {
  instanceOfUser,
  instanceOfUserGroup,
  PermissionType,
  type User,
} from "@dlr-shepard/backend-client";
import { useEditCollectionPermissions } from "../../../composables/common/permissions/useEditCollectionPermissions";
import type { Member } from "../../../composables/common/permissions/useMemberSearch";
import { mapMemberPermissions, mapPermissions } from "./mapPermissions";
import { UserRole } from "./UserRole";

interface EditPermissionsDialogProps {
  collectionId: number;
  isOwner?: boolean;
}

export interface MemberPermissions {
  member: Member;
  roleList: UserRole[];
}

const props = defineProps<EditPermissionsDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const isValid = ref(true);
const isOwnerInputValid = ref(true);

const selectedAdditionalUserRole = ref<UserRole | undefined>(undefined);

const memberPermissionsList = ref<MemberPermissions[] | undefined>(undefined);

const selectedMember = ref<Member | undefined>(undefined);

const filteredRoles = ref<UserRole[]>([]);

const { collectionPermissions, owner } = useFetchCollectionPermissions(
  props.collectionId,
);

const { updatedPermissions, saveChanges } = useEditCollectionPermissions(
  props.collectionId,
  () => (showDialog.value = false),
  isValid,
);

watch(
  collectionPermissions,
  async () => {
    if (collectionPermissions.value) {
      updatedPermissions.value = collectionPermissions.value;
      memberPermissionsList.value = await mapPermissions(
        updatedPermissions.value,
      );
    }
  },
  { once: true },
);

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
      role => role !== "Manager",
    );
  else filteredRoles.value = Object.values(UserRole);
});
</script>

<template>
  <Dialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    title="Manage Permissions"
    :max-width="950"
    :submit-disabled="!(isValid && isOwnerInputValid)"
    @submit="onSubmit"
  >
    <template #form>
      <v-form
        v-if="collectionPermissions"
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
              :model-value="collectionPermissions.permissionType"
              label="Permission Type"
              density="compact"
              :items="Object.values(PermissionType)"
              variant="outlined"
              @update:model-value="onPermissionTypeChange"
            />
          </v-col>
          <v-col>
            <OwnerAutocompleteInput
              v-if="owner"
              :model-value="owner"
              :is-owner="isOwner"
              @owner-change="onOwnerChange"
            />
          </v-col>
        </v-row>
        <v-divider
          opacity="100"
          class="text-divider1 mb-6 mt-2"
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
              label="User or group role"
              variant="outlined"
              density="compact"
              color="primary"
              require
              hide-details
            />
          </v-col>
          <v-col style="max-width: fit-content" class="d-flex align-center">
            <v-btn
              prepend-icon="mdi-plus-circle"
              color="primary"
              variant="flat"
              height="40px"
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
  </Dialog>
</template>
