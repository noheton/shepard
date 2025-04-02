<script setup lang="ts">
import {
  instanceOfUser,
  instanceOfUserGroup,
} from "@dlr-shepard/backend-client";
import type { MemberPermissions } from "./EditPermissionsDialog.vue";
import { UserRole } from "./UserRole";

const memberPermissionsList = defineModel<MemberPermissions[]>(
  "memberPermissions",
  {
    required: true,
  },
);

const PermissionAttributes = {
  Type: "type",
  Name: "name",
  RoleList: "roleList",
  Actions: "actions",
} as const;

const headers = [
  {
    key: PermissionAttributes.Type,
    width: "10%",
    cellProps: {
      class: "text-body-1",
    },
    value: (item: MemberPermissions) => (instanceOfUser(item.member) ? 1 : 0),
  },
  {
    title: "Name",
    key: PermissionAttributes.Name,
    width: "30%",
    cellProps: {
      class: "text-subtitle-2 word-wrap-anywhere",
    },
    value: (item: MemberPermissions) =>
      instanceOfUser(item.member)
        ? `${item.member.firstName} ${item.member.lastName}`
        : `${item.member.name}`,
  },
  {
    title: "Roles",
    key: PermissionAttributes.RoleList,
    width: "50%",
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
  {
    key: PermissionAttributes.Actions,
    width: "10%",
    sortable: false,
    cellProps: {
      class: "text-body-1 word-wrap-anywhere",
    },
  },
];

function deleteUserPermissions(deletedMember: MemberPermissions) {
  memberPermissionsList.value = memberPermissionsList.value.filter(
    memberPermissions => {
      if (instanceOfUser(memberPermissions.member)) {
        return (
          !instanceOfUser(deletedMember.member) ||
          memberPermissions.member.username !== deletedMember.member.username
        );
      }

      if (instanceOfUserGroup(deletedMember.member)) {
        return memberPermissions.member.id !== deletedMember.member.id;
      }

      return true;
    },
  );
}
function deleteRole(role: UserRole, entry: MemberPermissions) {
  // On deleting roles, implied roles are deleted automatically as well.
  if (role === UserRole.reader) {
    deleteRole(UserRole.manager, entry);
    deleteRole(UserRole.writer, entry);
  }
  if (role === UserRole.writer) {
    deleteRole(UserRole.manager, entry);
  }

  if (entry.roleList.length === 1 && entry.roleList[0] === role) {
    deleteUserPermissions(entry);
    return;
  }

  const memberToEdit = memberPermissionsList.value.find(memberPermissions => {
    if (instanceOfUser(entry.member)) {
      return (
        instanceOfUser(memberPermissions.member) &&
        memberPermissions.member.username === entry.member.username
      );
    }

    if (instanceOfUserGroup(memberPermissions.member)) {
      return memberPermissions.member.id === entry.member.id;
    }

    return false;
  });

  if (memberToEdit) {
    memberToEdit.roleList = memberToEdit.roleList.filter(
      memberRole => memberRole !== role,
    );
  }
}
</script>

<template>
  <DataTable
    :headers="headers"
    :items="memberPermissionsList"
    :sort-by="[{ key: PermissionAttributes.Name, order: 'asc' }]"
    hide-default-footer
    fixed-header
    style="max-height: 400px"
    :header-props="{
      class: 'bg-divider2 text-textbody1',
    }"
  >
    <template #[`item.type`]="{ item }: { item: MemberPermissions }">
      <v-icon
        :icon="
          instanceOfUser(item.member)
            ? 'mdi-account-outline'
            : 'mdi-account-multiple-outline'
        "
      />
    </template>
    <template #[`item.name`]="{ item }: { item: MemberPermissions }">
      {{
        instanceOfUser(item.member)
          ? `${item.member.firstName} ${item.member.lastName}`
          : item.member.name
      }}
    </template>
    <template #[`item.roleList`]="{ item }: { item: MemberPermissions }">
      <v-chip
        v-for="(role, key) in item.roleList"
        :key="key"
        size="small"
        border="sm"
        :class="[
          { 'manager-chip': role === UserRole.manager },
          { 'writer-chip': role === UserRole.writer },
          { 'reader-chip': role === UserRole.reader },
        ]"
        closable
      >
        <div class="text-body2-medium">
          {{ role }}
        </div>

        <template #close>
          <v-icon
            icon="mdi-close-circle-outline"
            size="x-small"
            @click.stop="deleteRole(role, item)"
          />
        </template>
      </v-chip>
    </template>
    <template #[`item.actions`]="{ item }: { item: MemberPermissions }">
      <v-btn
        icon="mdi-delete-outline"
        variant="plain"
        color="primary"
        @click="deleteUserPermissions(item)"
      />
    </template>
  </DataTable>
</template>

<style lang="scss" scoped>
:deep(.v-chip) {
  height: 22px;
  padding-left: 10px;
  padding-right: 10px;
  margin-right: 10px;
}

:deep(.v-chip.manager-chip) {
  border-color: rgb(var(--v-theme-green-stroke)) !important;
  .v-chip__underlay {
    opacity: 1;
    z-index: -1;
    background-color: rgb(var(--v-theme-green-background));
    color: rgb(var(--v-theme-green-background));
  }
  .v-chip__content {
    color: rgb(var(--v-theme-green-stroke));
  }
  .v-chip__close {
    color: rgb(var(--v-theme-green-stroke));
  }
}
:deep(.v-chip.reader-chip) {
  border-color: rgb(var(--v-theme-orange-stroke)) !important;
  .v-chip__underlay {
    opacity: 1;
    z-index: -1;
    background-color: rgb(var(--v-theme-orange-background));
    color: rgb(var(--v-theme-orange-background));
  }
  .v-chip__content {
    color: rgb(var(--v-theme-orange-stroke));
  }
  .v-chip__close {
    color: rgb(var(--v-theme-orange-stroke));
  }
}
:deep(.v-chip.writer-chip) {
  border-color: rgb(var(--v-theme-violet-stroke)) !important;
  .v-chip__underlay {
    opacity: 1;
    z-index: -1;
    background-color: rgb(var(--v-theme-violet-background));
    color: rgb(var(--v-theme-violet-background));
  }
  .v-chip__content {
    color: rgb(var(--v-theme-violet-stroke));
  }
  .v-chip__close {
    color: rgb(var(--v-theme-violet-stroke));
  }
}

:deep(.v-data-table__tr:hover) {
  background-color: rgb(var(--v-theme-focus1));
  .v-btn {
    visibility: visible !important;
  }
}

:deep(.v-btn) {
  visibility: hidden;
}
</style>
