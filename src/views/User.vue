<template>
  <div class="user">
    <div class="component">
      <h4>User Management</h4>
      <table v-if="user" class="table">
        <tbody>
          <tr>
            <th scope="row">Username</th>
            <td>{{ user.username }}</td>
          </tr>
          <tr>
            <th scope="row">First Name</th>
            <td>{{ user.firstName }}</td>
          </tr>
          <tr>
            <th scope="row">Last Name</th>
            <td>{{ user.lastName }}</td>
          </tr>
          <tr>
            <th scope="row">E-Mail</th>
            <td>{{ user.email }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <GenericCollapse v-if="user" title="Api Keys">
      <ApiKeyList :current-username="user.username" />
    </GenericCollapse>
    <GenericCollapse v-if="user" title="Subscriptions">
      <SubscriptionList :current-username="user.username" />
    </GenericCollapse>
  </div>
</template>

<script lang="ts">
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import ApiKeyList from "@/components/user/ApiKeysList.vue";
import SubscriptionList from "@/components/user/SubscriptionList.vue";
import { UserVue } from "@/utils/api-mixin";
import { User } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface UserData {
  userId?: number;
  user?: User;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof UserVue>>
).extend({
  components: {
    ApiKeyList,
    SubscriptionList,
    GenericCollapse,
  },
  mixins: [UserVue],
  data() {
    return {
      userId: undefined,
      user: undefined,
    } as UserData;
  },
  mounted() {
    this.fetchUser();
  },
  methods: {
    fetchUser() {
      this.userApi
        ?.getCurrentUser()
        .then(response => {
          this.user = response;
        })
        .catch(e => {
          const error = "Error while fetching user: " + e.statusText;
          console.log(error);
        });
    },
    userTableItems() {
      if (!this.user) {
        return [];
      }
      return [
        { "First Name": this.user.firstName },
        { "Last Name": this.user.lastName },
      ];
    },
  },
});
</script>
