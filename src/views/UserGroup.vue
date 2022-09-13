<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import UserModal from "@/components/user/UserModal.vue";
import UserGroupService from "@/services/userGroupService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  Permissions,
  ResponseError,
  User,
  UserGroup,
} from "@dlr-shepard/shepard-client";
import { computed, onMounted, onUpdated, ref, type Ref } from "vue";
import { createVuexHelpers } from "vue2-helpers";
import { useRoute, useRouter } from "vue2-helpers/vue-router";

const currentUserGroup: Ref<UserGroup | undefined> = ref();
const currentUser: Ref<string | undefined> = ref();
const permissions: Ref<Permissions | undefined> = ref();
const managerAccess = ref(false);

const route = useRoute();
const router = useRouter();
const currentUserGroupId = computed(() => {
  return Number(route.params.usergroupId);
});

const { useGetters, useActions } = createVuexHelpers();
const userCacheGetters = useGetters("userCache", [
  "isUserInCache",
  "getUserFromCache",
]);
const userCacheActions = useActions("userCache", ["fetchUser"]);
const isUserInCache: (username: string) => boolean =
  userCacheGetters.isUserInCache.value;
const getUserFromCache: (username: string) => User =
  userCacheGetters.getUserFromCache.value;
const fetchUser: (username: string) => void = userCacheActions.fetchUser;

function retrieveUser() {
  if (currentUserGroup.value)
    currentUserGroup.value.usernames.forEach(user => {
      if (!isUserInCache(user)) {
        fetchUser(user);
      }
    });
}

function retrieveUserGroup() {
  UserGroupService.getUserGroup({
    usergroupId: currentUserGroupId.value,
  })
    .then(response => {
      currentUserGroup.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching user group");
    });
}

function updateUserGroup() {
  if (currentUserGroup.value)
    UserGroupService.updateUserGroup({
      usergroupId: currentUserGroupId.value,
      userGroup: currentUserGroup.value,
    })
      .then(() => {
        retrieveUserGroup();
      })
      .catch(e => {
        handleError(e as ResponseError, "updating user group");
      });
}

function handleDeleteUserGroup() {
  UserGroupService.deleteUserGroup({
    usergroupId: currentUserGroupId.value,
  })
    .then(() => {
      router.push({
        name: "UserGroupList",
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting user group");
    });
}

function addUser(newUsernameList: Array<string>) {
  newUsernameList.forEach(user => {
    if (!currentUserGroup.value?.usernames.includes(user)) {
      currentUserGroup.value?.usernames.push(user);
    }
    updateUserGroup();
  });
}

function handleDeleteUser(delUser: string | undefined) {
  if (currentUserGroup.value && delUser) {
    currentUserGroup.value.usernames = currentUserGroup.value?.usernames.filter(
      user => user != delUser,
    );
    updateUserGroup();
  }
}

function retrievePermissions() {
  UserGroupService.getUserGroupPermissions({
    usergroupId: currentUserGroupId.value,
  })
    .then(response => {
      permissions.value = response;
      managerAccess.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
      managerAccess.value = e.status != 403;
    });
}

function updatePermissions(perms: Permissions) {
  UserGroupService.editUserGroupPermissions({
    usergroupId: currentUserGroupId.value,
    permissions: perms,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "updating permissions");
    });
}

onMounted(() => {
  retrieveUserGroup();
  retrievePermissions();
});
onUpdated(() => {
  retrieveUser();
});
</script>

<template>
  <div v-if="currentUserGroup">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.add-new-user-modal
          v-b-tooltip.hover
          title="Add User"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
        <b-button
          v-if="managerAccess"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="light"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-b-modal.delete-user-group-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>{{ currentUserGroup.name }}</h3>
      <p><b>ID:</b> {{ currentUserGroup.id }}<br /></p>

      <b-list-group v-if="currentUserGroup.usernames">
        <b-list-group-item
          v-for="(user, index) in currentUserGroup.usernames"
          :key="index"
        >
          <div class="float-left">
            <b v-if="getUserFromCache(user)">
              {{ getUserFromCache(user).lastName }},
              {{ getUserFromCache(user).firstName }}
            </b>
            |
            <small><GenericName :name="user" :word-count="40" /></small>
          </div>
          <b-button-group class="float-right">
            <b-button
              v-b-modal.delete-user-confirmation-modal
              v-b-tooltip.hover
              title="Delete"
              variant="dark"
              @click="currentUser = user"
            >
              <DeleteIcon />
            </b-button>
          </b-button-group>
        </b-list-group-item>
      </b-list-group>
    </div>
    <DeleteConfirmationModal
      v-if="currentUser"
      modal-id="delete-user-confirmation-modal"
      modal-name="Confirm to delete user from user group"
      :modal-text="
        'Do you really want do remove the user with name ' +
        currentUser +
        ', from the user group?'
      "
      @confirmation="handleDeleteUser(currentUser)"
    />
    <DeleteConfirmationModal
      modal-id="delete-user-group-confirmation-modal"
      modal-name="Confirm to delete user group"
      :modal-text="
        'Do you really want do delete the user group with name ' +
        currentUserGroup.name +
        '?'
      "
      @confirmation="handleDeleteUserGroup()"
    />

    <UserModal
      modal-id="add-new-user-modal"
      modal-name="Add user to user group"
      @add-user="addUser($event)"
    />

    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentUserGroupId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
