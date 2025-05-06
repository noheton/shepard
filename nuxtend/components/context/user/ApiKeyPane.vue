<script setup lang="ts">
import { ApikeyApi, UserApi } from "@dlr-shepard/backend-client";
import { toShortDateString } from "nuxtend/utils/helpers";
import AddApiKeyButton from "~/components/context/user/AddApiKeyButton.vue";

const apikeyApi = createApiInstance(ApikeyApi);
const userApi = createApiInstance(UserApi);

const user = await userApi.getCurrentUser();
const apiKeys = ref(await fetchApiKeys());

function fetchApiKeys() {
  return apikeyApi.getAllApiKeys({ username: user.username });
}

async function updateApiKeys() {
  apiKeys.value = await fetchApiKeys();
}
</script>

<template>
  <h4 class="text-h4">Api Keys</h4>
  <v-table hover>
    <thead>
      <tr>
        <td>Name</td>
        <td>Uid</td>
        <td>Created At</td>
      </tr>
    </thead>
    <tbody>
      <tr v-for="apiKey in apiKeys" :key="apiKey.createdAt.getMilliseconds()">
        <td>{{ apiKey.name }}</td>
        <td>{{ apiKey.uid }}</td>
        <td>{{ toShortDateString(apiKey.createdAt) }}</td>
      </tr>
    </tbody>
  </v-table>
  <AddApiKeyButton :username="user.username" @created="updateApiKeys" />
</template>

<style scoped lang="scss"></style>
