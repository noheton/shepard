<script setup lang="ts">
import {
  type ApiKey,
  ApikeyApi,
  type ResponseError,
  UserApi,
} from "@dlr-shepard/backend-client";
import { toShortDateTimeString } from "nuxtend/utils/helpers";
import AddApiKeyButton from "~/components/context/user/AddApiKeyButton.vue";
import DeleteApiKeyButton from "~/components/context/user/DeleteApiKeyButton.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const apikeyApi = useShepardApi(ApikeyApi);
const userApi = useShepardApi(UserApi);

const user = await userApi.value.getCurrentUser();
const apiKeys = ref<ApiKey[]>();

function fetchApiKeys() {
  return apikeyApi.value.getAllApiKeys({ username: user.username });
}

async function updateApiKeys() {
  try {
    apiKeys.value = await fetchApiKeys();
    apiKeys.value.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
  } catch (e) {
    handleError(e as ResponseError, "fetching api keys");
  }
}

updateApiKeys();
</script>

<template class="wrapper">
  <div class="top-row">
    <h4 class="text-h4">Api Keys</h4>
    <AddApiKeyButton :username="user.username" @created="updateApiKeys" />
  </div>
  <div v-if="apiKeys?.length == 0">No Api Keys found.</div>
  <v-table v-else hover>
    <thead>
      <tr>
        <th>Name</th>
        <th>Uid</th>
        <th>Created At</th>
        <th />
      </tr>
    </thead>
    <tbody>
      <tr v-for="apiKey in apiKeys" :key="apiKey.uid">
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
  margin-bottom: 16px;
}
.v-table {
  background-color: unset;

  :deep(thead) > tr > th {
    background-color: rgb(var(--v-theme-divider2));
  }

  :deep(td) {
    padding: 8px 24px !important;
  }

  :deep(tr):hover {
    background-color: rgb(var(--v-theme-focus1));
  }

  :deep(th) {
    font-size: 16px;
    padding: 8px 24px !important;
  }

  :deep(.mdi) {
    margin-left: 0.2em;
  }
}
</style>
