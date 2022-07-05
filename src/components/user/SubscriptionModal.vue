<template>
  <b-modal
    id="create-subscription-modal"
    title="Create Subscription"
    @show="subscriptionToCreate = { requestMethod: 'GET' }"
    @ok="$emit('create', subscriptionToCreate)"
  >
    <div v-if="subscriptionToCreate">
      <b-form-group label="Name">
        <b-form-input
          v-model="subscriptionToCreate.name"
          placeholder="My Subscription"
        ></b-form-input>
      </b-form-group>
      <b-form-group label="Callback URL">
        <b-form-input
          v-model="subscriptionToCreate.callbackURL"
          placeholder="http://my-server.example.com/callback-url"
        ></b-form-input>
      </b-form-group>
      <b-form-group label="Subscribed URL">
        <b-form-input
          v-model="subscriptionToCreate.subscribedURL"
          placeholder=".*/collections/123"
        ></b-form-input>
      </b-form-group>
      <b-form-group label="Request Method">
        <b-form-select
          v-model="subscriptionToCreate.requestMethod"
          :options="requestMethods"
        ></b-form-select>
      </b-form-group>
    </div>
  </b-modal>
</template>

<script lang="ts">
import {
  Subscription,
  SubscriptionRequestMethodEnum,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface SubscriptionListData {
  subscriptionToCreate?: Subscription;
}

export default defineComponent({
  data() {
    return {
      subscriptionToCreate: undefined,
      requestMethods: Object.values(SubscriptionRequestMethodEnum),
    } as SubscriptionListData;
  },
});
</script>
