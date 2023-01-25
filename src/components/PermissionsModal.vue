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
import { reactive, ref, watch, type PropType } from "vue";

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
const getInitialFormData = () => ({
  usernameOrGroupId: "",
  owner: undefined,
  reader: [],
  readerGroup: [],
  writer: [],
  writerGroup: [],
  manager: [],
  permissionType: undefined,
});

const formData = reactive<{
  usernameOrGroupId: string;
  owner?: User;
  reader: User[];
  readerGroup: UserGroup[];
  writer: User[];
  writerGroup: UserGroup[];
  manager: User[];
  permissionType?: PermissionsPermissionTypeEnum;
}>(getInitialFormData());
const validUser = ref<boolean>();
const validUserGroup = ref<boolean>();
const currentUser = ref<User>();
const currentUserGroup = ref<UserGroup>();

const emit = defineEmits(["update"]);
watch(
  () => props.oldPermissions,
  async () => parseOldPermissions(),
);

function reset() {
  Object.assign(formData, getInitialFormData());
  validUser.value = undefined;
  validUserGroup.value = undefined;
  currentUser.value = undefined;
  currentUserGroup.value = undefined;
}

function resetInput() {
  formData.usernameOrGroupId = "";
  validUser.value = undefined;
  validUserGroup.value = undefined;
}

function handleOk() {
  const perms: Permissions = {
    permissionType: formData.permissionType,
    owner: formData.owner?.username,
    reader: [],
    readerGroupIds: [],
    writer: [],
    writerGroupIds: [],
    manager: [],
  };
  formData.reader.forEach(u => {
    if (u.username) perms.reader.push(u.username);
  });
  formData.readerGroup.forEach(g => {
    if (g.id) perms.readerGroupIds?.push(g.id);
  });
  formData.writer.forEach(u => {
    if (u.username) perms.writer.push(u.username);
  });
  formData.writerGroup.forEach(g => {
    if (g.id) perms.writerGroupIds?.push(g.id);
  });
  formData.manager.forEach(u => {
    if (u.username) perms.manager.push(u.username);
  });
  emit("update", perms);
}

function setOwner() {
  if (currentUser.value) {
    formData.owner = currentUser.value;
    resetInput();
  }
}

function addReader() {
  if (currentUser.value && currentUser.value.username) {
    if (
      !formData.reader.some(
        user => user.username == currentUser.value?.username,
      )
    )
      formData.reader.push(currentUser.value);
    resetInput();
  } else if (currentUserGroup.value && currentUserGroup.value.name) {
    if (
      !formData.readerGroup.some(
        group => group.name == currentUserGroup.value?.name,
      )
    )
      formData.readerGroup.push(currentUserGroup.value);
    resetInput();
  }
}

function addWriter() {
  addReader();
  if (currentUser.value && currentUser.value.username) {
    if (
      !formData.writer.some(
        user => user.username == currentUser.value?.username,
      )
    )
      formData.writer.push(currentUser.value);
    resetInput();
  } else if (currentUserGroup.value && currentUserGroup.value.name) {
    if (
      !formData.writerGroup.some(
        group => group.name == currentUserGroup.value?.name,
      )
    )
      formData.writerGroup.push(currentUserGroup.value);
    resetInput();
  }
}

function addManager() {
  addReader();
  addWriter();
  if (currentUser.value && currentUser.value.username) {
    if (
      !formData.manager.some(
        user => user.username == currentUser.value?.username,
      )
    )
      formData.manager.push(currentUser.value);
    resetInput();
  }
}

function fetch() {
  if (!formData.usernameOrGroupId) {
    validUser.value = undefined;
    currentUser.value = undefined;
    validUserGroup.value = undefined;
    currentUserGroup.value = undefined;
    return;
  }
  if (Number(formData.usernameOrGroupId)) {
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
  UserService.getUser({ username: formData.usernameOrGroupId })
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
    usergroupId: Number(formData.usernameOrGroupId),
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
    formData.permissionType = perms.permissionType;
  }
  if (perms.owner) {
    UserService.getUser({ username: perms.owner }).then(user => {
      formData.owner = user;
    });
  }
  perms.reader.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      formData.reader.push(user),
    );
  });
  perms.readerGroupIds?.forEach(groupId => {
    UserGroupService.getUserGroup({ usergroupId: groupId }).then(usergroup =>
      formData.readerGroup.push(usergroup),
    );
  });
  perms.writer.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      formData.writer.push(user),
    );
  });
  perms.writerGroupIds?.forEach(groupId => {
    UserGroupService.getUserGroup({ usergroupId: groupId }).then(usergroup =>
      formData.writerGroup.push(usergroup),
    );
  });
  perms.manager.forEach(username => {
    UserService.getUser({ username: username }).then(user =>
      formData.manager.push(user),
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
      v-model="formData.permissionType"
      class="mb-3"
      :options="permissionOptions"
    ></b-form-select>

    <h5>Individual Permissions</h5>
    <b-input-group prepend="Username or GroupID">
      <b-form-input
        v-model="formData.usernameOrGroupId"
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
        v-if="formData.owner"
        class="d-flex justify-content-between align-items-center"
      >
        {{ formData.owner.firstName }} {{ formData.owner.lastName }}
        <b-button
          variant="secondary"
          size="sm"
          @click="formData.owner = undefined"
        >
          <DeleteIcon />
        </b-button>
      </b-list-group-item>
    </b-list-group>

    <b-row class="mt-3">
      <b-col cols="4">
        <div>Reader</div>
        <b-list-group>
          <b-list-group-item
            v-for="(user, index) in formData.reader"
            :key="user.username"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserIcon /> {{ user.firstName }} {{ user.lastName }}</div>
            <b-button
              variant="secondary"
              size="sm"
              @click="formData.reader.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>

          <b-list-group-item
            v-for="(group, index) in formData.readerGroup"
            :key="group.name"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserGroupIcon /> {{ group.name }}</div>
            <b-button
              variant="secondary"
              size="sm"
              @click="formData.readerGroup.splice(index, 1)"
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
            v-for="(user, index) in formData.writer"
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
              @click="formData.writer.splice(index, 1)"
            >
              <DeleteIcon />
            </b-button>
          </b-list-group-item>
          <b-list-group-item
            v-for="(group, index) in formData.writerGroup"
            :key="group.name"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserGroupIcon /> {{ group.name }}</div>
            <b-button
              variant="secondary"
              size="sm"
              @click="formData.writerGroup.splice(index, 1)"
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
            v-for="(user, index) in formData.manager"
            :key="user.username"
            class="d-flex justify-content-between align-items-center"
          >
            <div><UserIcon /> {{ user.firstName }} {{ user.lastName }}</div>
            <b-button
              variant="secondary"
              size="sm"
              @click="formData.manager.splice(index, 1)"
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
