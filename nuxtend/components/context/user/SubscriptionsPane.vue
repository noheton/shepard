<script setup lang="ts">
import {
  type ResponseError,
  type Subscription,
  SubscriptionApi,
  UserApi,
} from "@dlr-shepard/backend-client";
import AddSubscriptionButton from "~/components/context/user/AddSubscriptionButton.vue";
import DeleteSubscriptionButton from "~/components/context/user/DeleteSubscriptionButton.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const subscriptionsApi = useShepardApi(SubscriptionApi);
const userApi = useShepardApi(UserApi);

const user = await userApi.value.getCurrentUser();
const subscriptions = ref<Subscription[]>();

function fetchSubscriptions() {
  return subscriptionsApi.value.getAllSubscriptions({
    username: user.username,
  });
}

async function updateSubscriptions() {
  try {
    subscriptions.value = await fetchSubscriptions();
    subscriptions.value.sort(
      (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
    );
  } catch (e) {
    handleError(e as ResponseError, "fetching api keys");
  }
}

updateSubscriptions();
</script>

<template class="wrapper">
  <div class="top-row">
    <h4 class="text-h4">Subscriptions</h4>
    <AddSubscriptionButton
      :username="user.username"
      @created="updateSubscriptions"
    />
  </div>
  <div v-if="subscriptions?.length == 0">No subscriptions found.</div>
  <v-table v-else hover>
    <thead>
      <tr>
        <th>Name</th>
        <th>ID</th>
        <th>Callback URL</th>
        <th>Subscribed URL</th>
        <th>Request method</th>
        <th />
      </tr>
    </thead>
    <tbody>
      <tr v-for="subscription in subscriptions" :key="subscription.id">
        <td>{{ subscription.name }}</td>
        <td>{{ subscription.id }}</td>
        <td>{{ subscription.callbackURL }}</td>
        <td>{{ subscription.subscribedURL }}</td>
        <td>{{ subscription.requestMethod }}</td>
        <td class="action-column">
          <DeleteSubscriptionButton
            :username="user.username"
            :subscription-id="subscription.id"
            @deleted="updateSubscriptions"
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
