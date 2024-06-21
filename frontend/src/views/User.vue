<script setup lang="ts">
import GenericCollapse from "@/components/generic/GenericCollapse.vue";
import ApiKeyList from "@/components/user/ApiKeysList.vue";
import SubscriptionList from "@/components/user/SubscriptionList.vue";
import UserService from "@/services/userService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError, User } from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { onMounted, ref } from "vue";

const user = ref<User>();

function fetchUser() {
  UserService.getCurrentUser()
    .then(response => {
      user.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching user");
    });
}

onMounted(() => {
  fetchUser();
  useTitle("User | shepard");
});
</script>

<template>
  <div class="view">
    <h4 class="title">User Management</h4>
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
    <GenericCollapse v-if="user" class="mb-3" title="Api Keys">
      <ApiKeyList :current-username="user.username || ''" />
    </GenericCollapse>
    <GenericCollapse v-if="user" class="mb-3" title="Subscriptions">
      <SubscriptionList :current-username="user.username || ''" />
    </GenericCollapse>
  </div>
</template>
