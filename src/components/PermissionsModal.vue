<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    lazy
    @show="reset()"
    @ok="handleOk()"
  >
    <h5>Permission Type</h5>
    <b-form-select
      v-model="permissionType"
      class="mb-3"
      :options="permissionOptions"
    ></b-form-select>

    <h5>Individual Permissions</h5>
    <b-input-group prepend="Username or GroupID">
      <b-form-input
        v-model="usernameOrGroupId"
        :state="validUser || validUserGroup"
        @change="fetch()"
      ></b-form-input>
      <b-input-group-append>
        <b-dropdown text="Add">
          <b-dropdown-item :disabled="!validUser" @click="setOwner()">
            Owner
          </b-dropdown-item>
          <b-dropdown-item
            :disabled="!validUser && !validUserGroup"
            @click="addReader()"
          >
            Reader
          </b-dropdown-item>
          <b-dropdown-item
            :disabled="!validUser && !validUserGroup"
            @click="addWriter()"
          >
            Writer
          </b-dropdown-item>
          <b-dropdown-item :disabled="!validUser" @click="addManager()">
            Manager
          </b-dropdown-item>
        </b-dropdown>
      </b-input-group-append>
    </b-input-group>
    <small v-if="currentUser">
      <em> {{ currentUser.firstName }} {{ currentUser.lastName }} </em>
    </small>
    <small v-else-if="currentUserGroup">
      <em> {{ currentUserGroup.name }} </em>
    </small>
    <small v-else>Please enter a valid username</small>

    <div class="mt-3">Owner</div>
    <b-list-group>
      <b-list-group-item
        v-if="owner"
        class="d-flex justify-content-between align-items-center"
      >
        {{ owner.firstName }} {{ owner.lastName }}
        <b-button variant="light" size="sm" @click="owner = undefined">
          <DeleteIcon />
        </b-button>
      </b-list-group-item>
    </b-list-group>

    <b-row class="mt-3">
      <b-col cols="4">
        <div>Reader</div>
        <b-list-group>
          <b-list-group-item
            v-for="(user, index) in reader"
            :key="user.username"
            class="d-flex justify-content-between align-items-center"
          >
            <div>
              <UserIcon :size="18" /> {{ user.firstName }} {{ user.lastName }}
            </div>
            <b-button
              variant="light"
              size="sm"
              @click="reader.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>

          <b-list-group-item
            v-for="(group, index) in readerGroup"
            :key="group.name"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserGroupIcon :size="18" /> {{ group.name }}</div>
            <b-button
              variant="light"
              size="sm"
              @click="readerGroup.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>
        </b-list-group>
      </b-col>
      <b-col cols="4">
        <div>Writer</div>
        <b-list-group>
          <b-list-group-item
            v-for="(user, index) in writer"
            :key="user.username"
            class="d-flex justify-content-between align-items-center"
          >
            <div>
              <UserIcon :size="18" /> {{ user.firstName }}
              {{ user.lastName }}
            </div>
            <b-button
              variant="light"
              size="sm"
              @click="writer.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>
          <b-list-group-item
            v-for="(group, index) in writerGroup"
            :key="group.name"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserGroupIcon :size="18" /> {{ group.name }}</div>
            <b-button
              variant="light"
              size="sm"
              @click="writerGroup.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>
        </b-list-group>
      </b-col>
      <b-col cols="4">
        <div>Manager</div>
        <b-list-group>
          <b-list-group-item
            v-for="(user, index) in manager"
            :key="user.username"
            class="d-flex justify-content-between align-items-center"
          >
            <div>
              <UserIcon :size="18" /> {{ user.firstName }} {{ user.lastName }}
            </div>
            <b-button
              variant="light"
              size="sm"
              @click="manager.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>
        </b-list-group>
      </b-col>
    </b-row>
  </b-modal>
</template>

<script lang="ts">
import UserGroupService from "@/services/userGroupService";
import UserService from "@/services/userService";
import {
  Permissions,
  PermissionsPermissionTypeEnum,
  User,
  UserGroup,
} from "@dlr-shepard/shepard-client";
import { defineComponent, PropType } from "vue";

interface PermissionsModalData {
  usernameOrGroupId: string;
  validUser?: boolean;
  validUserGroup?: boolean;
  currentUser?: User;
  currentUserGroup?: UserGroup;
  owner?: User;
  reader: User[];
  readerGroup: UserGroup[];
  writer: User[];
  writerGroup: UserGroup[];
  manager: Array<User>;
  permissionOptions: { value: PermissionsPermissionTypeEnum; text: string }[];
  permissionType?: PermissionsPermissionTypeEnum;
}

function initialState(): PermissionsModalData {
  return {
    usernameOrGroupId: "",
    validUser: undefined,
    validUserGroup: undefined,
    currentUser: undefined,
    currentUserGroup: undefined,
    owner: undefined,
    reader: [],
    readerGroup: [],
    writer: [],
    writerGroup: [],
    manager: [],
    permissionOptions: [
      {
        value: PermissionsPermissionTypeEnum.Private,
        text: "Private",
      },
      {
        value: PermissionsPermissionTypeEnum.PublicReadable,
        text: "Public Readable",
      },
      {
        value: PermissionsPermissionTypeEnum.Public,
        text: "Public",
      },
    ],
    permissionType: undefined,
  };
}

export default defineComponent({
  props: {
    modalId: {
      type: String,
      default: "PermissionsModal",
    },
    modalName: {
      type: String,
      default: "PermissionsModal",
    },
    entityId: {
      type: Number,
      required: true,
    },
    oldPermissions: {
      type: Object as PropType<Permissions>,
      default: () => {
        return { owner: undefined, reader: [], writer: [], manager: [] };
      },
    },
  },
  data() {
    return initialState();
  },
  methods: {
    reset() {
      Object.assign(this.$data, initialState());
      this.parseOldPermissions();
    },
    resetInput() {
      this.usernameOrGroupId = "";
      this.validUser = undefined;
      this.validUserGroup = undefined;
    },
    handleOk() {
      const perms: Permissions = {
        permissionType: this.permissionType,
        owner: this.owner?.username,
        reader: [],
        readerGroupIds: [],
        writer: [],
        writerGroupIds: [],
        manager: [],
      };
      this.reader.forEach(u => {
        if (u.username) perms.reader.push(u.username);
      });
      this.readerGroup.forEach(g => {
        if (g.id) perms.readerGroupIds?.push(g.id);
      });
      this.writer.forEach(u => {
        if (u.username) perms.writer.push(u.username);
      });
      this.writerGroup.forEach(g => {
        if (g.id) perms.writerGroupIds?.push(g.id);
      });
      this.manager.forEach(u => {
        if (u.username) perms.manager.push(u.username);
      });
      this.$emit("update", perms);
    },
    setOwner() {
      if (this.currentUser) {
        this.owner = this.currentUser;
        this.resetInput();
      }
    },
    addReader() {
      if (this.currentUser && this.currentUser.username) {
        if (
          !this.reader.some(user => user.username == this.currentUser?.username)
        )
          this.reader.push(this.currentUser);
        this.resetInput();
      } else if (this.currentUserGroup && this.currentUserGroup.name) {
        if (
          !this.readerGroup.some(
            group => group.name == this.currentUserGroup?.name,
          )
        )
          this.readerGroup.push(this.currentUserGroup);
        this.resetInput();
      }
    },
    addWriter() {
      this.addReader();
      if (this.currentUser && this.currentUser.username) {
        if (
          !this.writer.some(user => user.username == this.currentUser?.username)
        )
          this.writer.push(this.currentUser);
        this.resetInput();
      } else if (this.currentUserGroup && this.currentUserGroup.name) {
        if (
          !this.writerGroup.some(
            group => group.name == this.currentUserGroup?.name,
          )
        )
          this.writerGroup.push(this.currentUserGroup);
        this.resetInput();
      }
    },
    addManager() {
      this.addReader();
      this.addWriter();
      if (this.currentUser && this.currentUser.username) {
        if (
          !this.manager.some(
            user => user.username == this.currentUser?.username,
          )
        )
          this.manager.push(this.currentUser);
        this.resetInput();
      }
    },
    fetch() {
      if (!this.usernameOrGroupId) {
        this.validUser = undefined;
        this.currentUser = undefined;
        this.validUserGroup = undefined;
        this.currentUserGroup = undefined;
        return;
      }
      if (Number(this.usernameOrGroupId)) {
        this.fetchUserGroups();
        this.validUser = false;
        this.currentUser = undefined;
      } else {
        this.fetchUser();
        this.validUserGroup = false;
        this.currentUserGroup = undefined;
      }
    },
    fetchUser() {
      UserService.getUser({ username: this.usernameOrGroupId })
        .then(currentUser => {
          this.currentUser = currentUser;
          this.validUser = true;
        })
        .catch(e => {
          const error = "Error while fetching user: " + e.statusText;
          console.log(error);
          this.currentUser = undefined;
          this.validUser = false;
        });
    },
    fetchUserGroups() {
      UserGroupService.getUserGroup({
        usergroupId: Number(this.usernameOrGroupId),
      })
        .then(currentUserGroup => {
          this.currentUserGroup = currentUserGroup;
          this.validUserGroup = true;
        })
        .catch(e => {
          const error = "Error while fetching userGroup: " + e.statusText;
          console.log(error);
          this.currentUserGroup = undefined;
          this.validUserGroup = false;
        });
    },
    parseOldPermissions() {
      const perms: Permissions = this.oldPermissions;
      if (!perms) return;
      if (perms.permissionType) {
        this.permissionType = perms.permissionType;
      }
      if (perms.owner) {
        UserService.getUser({ username: perms.owner }).then(
          owner => (this.owner = owner),
        );
      }
      perms.reader.forEach(username => {
        UserService.getUser({ username: username }).then(user =>
          this.reader.push(user),
        );
      });
      perms.readerGroupIds?.forEach(groupId => {
        UserGroupService.getUserGroup({ usergroupId: groupId }).then(
          usergroup => this.readerGroup.push(usergroup),
        );
      });
      perms.writer.forEach(username => {
        UserService.getUser({ username: username }).then(user =>
          this.writer.push(user),
        );
      });
      perms.writerGroupIds?.forEach(groupId => {
        UserGroupService.getUserGroup({ usergroupId: groupId }).then(
          usergroup => this.writerGroup.push(usergroup),
        );
      });
      perms.manager.forEach(username => {
        UserService.getUser({ username: username }).then(user =>
          this.manager.push(user),
        );
      });
    },
  },
});
</script>

<style scoped>
h5 {
  margin-top: 10px;
}

.list-group-item {
  padding: 0rem;
  padding-left: 0.5rem;
}
</style>
