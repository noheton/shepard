<script setup lang="ts">
import UserGroupService from "@/services/userGroupService";
import UserService from "@/services/userService";
import { logError } from "@/utils/error-handling";
import { permissionOptions as pOptions } from "@/utils/helpers";
import type {
  Permissions,
  PermissionsPermissionTypeEnum,
  ResponseError,
  User,
  UserGroup,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type PropType } from "vue";

const props = defineProps({
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
});

const permissionOptions = pOptions;

const usernameOrGroupId = ref("");
const validUser = ref<boolean>();
const validUserGroup = ref<boolean>();
const currentUser = ref<User>();
const currentUserGroup = ref<UserGroup>();
const owner = ref<User>();
const reader = ref<User[]>([]);
const readerGroup = ref<UserGroup[]>([]);
const writer = ref<User[]>([]);
const writerGroup = ref<UserGroup[]>([]);
const manager = ref<User[]>([]);
const permissionType = ref<PermissionsPermissionTypeEnum>();

const emit = defineEmits(["update"]);
watch(
  () => props.oldPermissions,
  async () => parseOldPermissions(),
);

function reset() {
  usernameOrGroupId.value = "";
  validUser.value = undefined;
  validUserGroup.value = undefined;
  currentUser.value = undefined;
  currentUserGroup.value = undefined;
  owner.value = undefined;
  reader.value = [];
  readerGroup.value = [];
  writer.value = [];
  writerGroup.value = [];
  manager.value = [];
  permissionType.value = undefined;
}

function resetInput() {
  usernameOrGroupId.value = "";
  validUser.value = undefined;
  validUserGroup.value = undefined;
}

function handleOk() {
  const perms: Permissions = {
    permissionType: permissionType.value,
    owner: owner.value?.username,
    reader: [],
    readerGroupIds: [],
    writer: [],
    writerGroupIds: [],
    manager: [],
  };
  reader.value.forEach(u => {
    if (u.username) perms.reader.push(u.username);
  });
  readerGroup.value.forEach(g => {
    if (g.id) perms.readerGroupIds?.push(g.id);
  });
  writer.value.forEach(u => {
    if (u.username) perms.writer.push(u.username);
  });
  writerGroup.value.forEach(g => {
    if (g.id) perms.writerGroupIds?.push(g.id);
  });
  manager.value.forEach(u => {
    if (u.username) perms.manager.push(u.username);
  });
  emit("update", perms);
}

function setOwner() {
  if (currentUser.value) {
    owner.value = currentUser.value;
    resetInput();
  }
}

function addReader() {
  if (currentUser.value && currentUser.value.username) {
    if (
      !reader.value.some(user => user.username == currentUser.value?.username)
    )
      reader.value.push(currentUser.value);
    resetInput();
  } else if (currentUserGroup.value && currentUserGroup.value.name) {
    if (
      !readerGroup.value.some(
        group => group.name == currentUserGroup.value?.name,
      )
    )
      readerGroup.value.push(currentUserGroup.value);
    resetInput();
  }
}

function addWriter() {
  addReader();
  if (currentUser.value && currentUser.value.username) {
    if (
      !writer.value.some(user => user.username == currentUser.value?.username)
    )
      writer.value.push(currentUser.value);
    resetInput();
  } else if (currentUserGroup.value && currentUserGroup.value.name) {
    if (
      !writerGroup.value.some(
        group => group.name == currentUserGroup.value?.name,
      )
    )
      writerGroup.value.push(currentUserGroup.value);
    resetInput();
  }
}

function addManager() {
  addReader();
  addWriter();
  if (currentUser.value && currentUser.value.username) {
    if (
      !manager.value.some(user => user.username == currentUser.value?.username)
    )
      manager.value.push(currentUser.value);
    resetInput();
  }
}

function fetch() {
  if (!usernameOrGroupId.value) {
    validUser.value = undefined;
    currentUser.value = undefined;
    validUserGroup.value = undefined;
    currentUserGroup.value = undefined;
    return;
  }
  if (Number(usernameOrGroupId)) {
    fetchUserGroups();
    validUser.value = false;
    currentUser.value = undefined;
  } else {
    fetchUser();
    validUserGroup.value = false;
    currentUserGroup.value = undefined;
  }
}

function fetchUser() {
  UserService.getUser({ username: usernameOrGroupId.value })
    .then(user => {
      currentUser.value = user;
      validUser.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching user");
      currentUser.value = undefined;
      validUser.value = false;
    });
}

function fetchUserGroups() {
  UserGroupService.getUserGroup({
    usergroupId: Number(usernameOrGroupId),
  })
    .then(userGroup => {
      currentUserGroup.value = userGroup;
      validUserGroup.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching user group");
      currentUserGroup.value = undefined;
      validUserGroup.value = false;
    });
}

function parseOldPermissions() {
  const perms: Permissions = props.oldPermissions;
  if (!perms) return;
  if (perms.permissionType) {
    permissionType.value = perms.permissionType;
  }
  if (perms.owner) {
    UserService.getUser({ username: perms.owner }).then(user => {
      owner.value = user;
    });
  }
  perms.reader.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      reader.value.push(user),
    );
  });
  perms.readerGroupIds?.forEach(groupId => {
    UserGroupService.getUserGroup({ usergroupId: groupId }).then(usergroup =>
      readerGroup.value.push(usergroup),
    );
  });
  perms.writer.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      writer.value.push(user),
    );
  });
  perms.writerGroupIds?.forEach(groupId => {
    UserGroupService.getUserGroup({ usergroupId: groupId }).then(usergroup =>
      writerGroup.value.push(usergroup),
    );
  });
  perms.manager.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      manager.value.push(user),
    );
  });
}
</script>

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
        <b-dropdown text="Add" variant="info">
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
        <b-button variant="secondary" size="sm" @click="owner = undefined">
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
            <div><UserIcon /> {{ user.firstName }} {{ user.lastName }}</div>
            <b-button
              variant="secondary"
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
            <div><UserGroupIcon /> {{ group.name }}</div>
            <b-button
              variant="secondary"
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
              <UserIcon /> {{ user.firstName }}
              {{ user.lastName }}
            </div>
            <b-button
              variant="secondary"
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
            <div><UserGroupIcon /> {{ group.name }}</div>
            <b-button
              variant="secondary"
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
            <div><UserIcon /> {{ user.firstName }} {{ user.lastName }}</div>
            <b-button
              variant="secondary"
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

<style scoped>
h5 {
  margin-top: 10px;
}

.list-group-item {
  padding: 0rem;
  padding-left: 0.5rem;
}
</style>
