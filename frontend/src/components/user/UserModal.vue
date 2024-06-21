<script setup lang="ts">
import { useSearchUsers } from "@/components/search/InlineSearchUsers";
import UserSelectionPopover from "@/components/user/UserSelectionPopover.vue";
import UserService from "@/services/userService";
import { logError } from "@/utils/error-handling";
import type { ResponseError, User } from "@dlr-shepard/shepard-client";
import { refDebounced } from "@vueuse/core";
import { ref } from "vue";

const validUser = ref<boolean>();
const currentUser = ref<User>();
const newUserList = ref<User[]>([]);
const newUsernameList = ref<string[]>([]);

defineProps({
  modalId: {
    type: String,
    default: "UserModal",
  },
  modalName: {
    type: String,
    default: "UserModal",
  },
});

const emit = defineEmits(["add-user"]);

const userInputSearchUser = ref("");
const userInputSearchUserDebounced = refDebounced(userInputSearchUser, 700);
const { results } = useSearchUsers(userInputSearchUserDebounced);

function reset() {
  userInputSearchUser.value = "";
  validUser.value = undefined;
  currentUser.value = undefined;
  newUserList.value = [];
  newUsernameList.value = [];
}

function handleOk() {
  newUserList.value.forEach(user => {
    if (user.username) {
      newUsernameList.value.push(user.username);
    }
  });
  emit("add-user", newUsernameList.value);
}

function fetchUser(username: string) {
  if (!userInputSearchUser.value) {
    validUser.value = undefined;
    currentUser.value = undefined;
    return;
  }
  UserService.getUser({ username: username })
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

function chooseUser(user: User) {
  if (user.username) {
    userInputSearchUser.value = user.username;
    fetchUser(user.username);
    addUser(user);
  }
}

function addUser(user: User) {
  if (
    !newUserList.value.some(
      user => user.username == currentUser.value?.username,
    )
  )
    newUserList.value.push(user);
}

function deleteUser(index: number) {
  newUserList.value.splice(index, 1);
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="lg"
    :title="modalName"
    :ok-disabled="newUserList.length === 0"
    @show="reset()"
    @ok="handleOk()"
  >
    <b-input-group prepend="Username">
      <b-form-input
        id="userFormInput"
        v-model="userInputSearchUser"
        placeholder="User Name"
        :state="validUser"
        @blur="fetchUser(userInputSearchUser)"
      ></b-form-input>
    </b-input-group>

    <small v-if="currentUser">
      <em> {{ currentUser.firstName }} {{ currentUser.lastName }} </em>
    </small>
    <small v-else>Please enter a valid username</small>

    <UserSelectionPopover
      :results="results"
      title-text="search for user by name, mail or username"
      @selected="chooseUser($event)"
    />

    <div v-if="newUserList" class="mt-4">List of users to add</div>
    <b-list-group>
      <b-list-group-item
        v-for="(user, index) in newUserList"
        :key="user.username"
        class="d-flex justify-content-between align-items-center"
      >
        {{ user.firstName }} {{ user.lastName }}
        <b-button variant="secondary" size="sm" @click="deleteUser(index)">
          <DeleteIcon />
        </b-button>
      </b-list-group-item>
    </b-list-group>
  </b-modal>
</template>

<style scoped>
.list-group-item {
  padding: 0rem;
  padding-left: 1rem;
}
</style>
