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
        v-model="username"
        placeholder="User Name"
        :state="validUser"
        @blur="validateUser()"
      ></b-form-input>
      <b-input-group-append>
        <b-button text="Add" @click="addUser()">Add</b-button>
      </b-input-group-append>
    </b-input-group>

    <small v-if="currentUser">
      <em> {{ currentUser.firstName }} {{ currentUser.lastName }} </em>
    </small>
    <small v-else>Please enter a valid username</small>

    <div v-if="newUserList" class="mt-4">List of users to add</div>
    <b-list-group>
      <b-list-group-item
        v-for="(user, index) in newUserList"
        :key="user.username"
        class="d-flex justify-content-between align-items-center"
      >
        {{ user.firstName }} {{ user.lastName }}
        <b-button variant="light" size="sm" @click="deleteUser(index)">
          <DeleteIcon />
        </b-button>
      </b-list-group-item>
    </b-list-group>
  </b-modal>
</template>

<script lang="ts">
import { UserVue } from "@/utils/api-mixin";
import { User } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface AddUserModalData {
  username: string;
  validUser?: boolean;
  currentUser?: User;
  newUserList: User[];
  newUsernameList: Array<string>;
}

function initialState(): AddUserModalData {
  return {
    username: "",
    validUser: undefined,
    currentUser: undefined,
    newUserList: [],
    newUsernameList: [],
  };
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof UserVue>>
).extend({
  mixins: [UserVue],
  props: {
    modalId: {
      type: String,
      default: "UserModal",
    },
    modalName: {
      type: String,
      default: "UserModal",
    },
  },
  data() {
    return initialState();
  },
  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },
    handleOk() {
      this.newUserList.forEach(user => {
        if (user.username) {
          this.newUsernameList.push(user.username);
        }
      });
      this.$emit("add-user", this.newUsernameList);
    },
    validateUser() {
      if (!this.username) {
        this.validUser = undefined;
        this.currentUser = undefined;
        return;
      }
      this.userApi
        ?.getUser({ username: this.username })
        .then(currentUser => {
          this.currentUser = currentUser;
          this.validUser = true;
        })
        .catch(e => {
          const error = "Error while getting user: " + e.statusText;
          console.log(error);
          this.currentUser = undefined;
          this.validUser = false;
        });
    },
    addUser() {
      if (this.currentUser && this.currentUser.username) {
        if (
          !this.newUserList.some(
            user => user.username == this.currentUser?.username,
          )
        )
          this.newUserList.push(this.currentUser);
      }
    },

    deleteUser(index: number) {
      this.newUserList.splice(index, 1);
    },
  },
});
</script>

<style scoped>
.list-group-item {
  padding: 0rem;
  padding-left: 1rem;
}
</style>
