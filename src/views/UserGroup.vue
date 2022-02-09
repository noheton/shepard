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

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import UserModal from "@/components/user/UserModal.vue";
import { UserGroupVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import { Permissions, UserGroup } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";
import { mapActions, mapGetters } from "vuex";

interface UserGroupData {
  currentUserGroup?: UserGroup;
  currentUser?: string;
  newUser?: string;
  validUser?: boolean;
  permissions?: Permissions;
  managerAccess: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof UserGroupVue>>
).extend({
  components: {
    DeleteConfirmationModal,
    PermissionsModal,
    GenericName,
    UserModal,
  },
  mixins: [UserGroupVue],
  data() {
    return {
      currentUserGroup: undefined,
      currentUser: undefined,
      newUser: undefined,
      validUser: undefined,
      permissions: undefined,
      managerAccess: false,
    } as UserGroupData;
  },
  computed: {
    currentUserGroupId(): number {
      return Number(this.$router.currentRoute.params.usergroupId);
    },
    ...mapGetters("userCache", ["isUserInCache", "getUserFromCache"]),
  },
  updated() {
    this.retrieveUser();
  },
  mounted() {
    this.retrieveUserGroup();
    this.retrievePermissions();
  },

  methods: {
    ...mapActions("userCache", ["fetchUser"]),
    retrieveUser() {
      if (this.currentUserGroup)
        this.currentUserGroup.usernames.forEach(user => {
          if (!this.isUserInCache(user)) {
            this.fetchUser(user);
          }
        });
    },

    retrieveUserGroup() {
      this.userGroupApi
        ?.getUserGroup({
          usergroupId: this.currentUserGroupId,
        })
        .then(response => {
          this.currentUserGroup = response;
        })
        .catch(e => {
          const error = "Error while fetching user group: " + e.statusText;
          console.log(error);
        });
    },
    validateUser() {
      try {
        this.fetchUser(this.newUser);
        this.validUser = true;
      } catch (error) {
        this.validUser = false;
      }
    },
    updateUserGroup() {
      if (this.currentUserGroup)
        this.userGroupApi
          ?.updateUserGroup({
            usergroupId: this.currentUserGroupId,
            userGroup: this.currentUserGroup,
          })
          .then(() => {
            this.retrieveUserGroup();
          })
          .catch(e => {
            const error = "Error while updating a user group: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
          });
    },
    handleDeleteUserGroup() {
      this.userGroupApi
        ?.deleteUserGroup({
          usergroupId: this.currentUserGroupId,
        })
        .then(() => {
          this.$router.push({
            name: "UserGroupList",
          });
        })
        .catch(e => {
          const error = "Error while deleting user group: " + e.statusText;
          console.log(error);
        });
    },
    addUser(newUsernameList: Array<string>) {
      newUsernameList.forEach(user => {
        if (!this.currentUserGroup?.usernames.includes(user)) {
          this.currentUserGroup?.usernames.push(user);
        }
        this.updateUserGroup();
      });
    },
    handleDeleteUser(delUser: string) {
      if (this.currentUserGroup) {
        this.currentUserGroup.usernames =
          this.currentUserGroup?.usernames.filter(user => user != delUser);
        this.updateUserGroup();
      }
    },
    retrievePermissions() {
      this.userGroupApi
        ?.getUserGroupPermissions({ usergroupId: this.currentUserGroupId })
        .then(response => {
          this.permissions = response;
          this.managerAccess = true;
        })
        .catch(e => {
          const error = "Error while fetching permissons: " + e.statusText;
          console.log(error);
          this.managerAccess = e.status != 403;
        });
    },
    updatePermissions(perms: Permissions) {
      this.userGroupApi
        ?.editUserGroupPermissions({
          usergroupId: this.currentUserGroupId,
          permissions: perms,
        })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while editing permissons: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
