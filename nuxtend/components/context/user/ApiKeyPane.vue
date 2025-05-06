<script setup lang="ts">
import { type ApiKey, ApikeyApi, UserApi } from "@dlr-shepard/backend-client";
import { toShortDateString } from "nuxtend/utils/helpers";

const apikeyApi = createApiInstance(ApikeyApi);
const userApi = createApiInstance(UserApi);
const showCreateDialog = ref(false);
const newKeyName = ref("");

const user = await userApi.getCurrentUser();
const apiKeys = ref(await apikeyApi.getAllApiKeys({ username: user.username }));

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
    <template #prepend>
      <v-icon color="canvas" icon="mdi-plus-circle" />
    </template>
    Add Api Key
  </v-btn>
</template>

<style scoped lang="scss"></style>
