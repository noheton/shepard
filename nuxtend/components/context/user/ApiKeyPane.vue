<script setup lang="ts">
import { type ApiKey, ApikeyApi, UserApi } from "@dlr-shepard/backend-client";
import {
  toDateTimeString,
  toShortDateString,
  toShortDateTimeString,
} from "nuxtend/utils/helpers";
import AddApiKeyButton from "~/components/context/user/AddApiKeyButton.vue";
import DeleteApiKeyButton from "~/components/context/user/DeleteApiKeyButton.vue";

const apikeyApi = createApiInstance(ApikeyApi);
const userApi = createApiInstance(UserApi);

const user = await userApi.getCurrentUser();
const apiKeys = ref<ApiKey[]>();

function fetchApiKeys() {
  return apikeyApi.getAllApiKeys({ username: user.username });
}

async function updateApiKeys() {
  apiKeys.value = await fetchApiKeys();
  apiKeys.value.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
}

updateApiKeys();
</script>

<template class="wrapper">
  <div class="top-row">
    <h4 class="text-h4">Api Keys</h4>
    <AddApiKeyButton :username="user.username" @created="updateApiKeys" />
  </div>
  <v-table hover>
    <thead>
      <tr>
        <th>Name</th>
        <th>Uid</th>
        <th>Created At</th>
        <th />
      </tr>
    </thead>
    <tbody>
      <tr v-for="apiKey in apiKeys" :key="apiKey.createdAt.getTime()">
        <td>{{ apiKey.name }}</td>
        <td class="uid-column">{{ apiKey.uid }}</td>
        <td>{{ toShortDateTimeString(apiKey.createdAt) }}</td>
        <td class="action-column">
          <DeleteApiKeyButton
            :username="user.username"
            :apikey-uid="apiKey.uid"
            @deleted="updateApiKeys"
          />
        </td>
      </tr>
    </tbody>
  </v-table>
</template>

<style scoped lang="scss">
td {
  white-space: nowrap;
}
.uid-column {
  width: 100%;
}
.action-column {
  text-align: center;
}
.top-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}
</style>
