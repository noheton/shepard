<script setup lang="ts">
import {
  SubscriptionRequestMethodEnum,
  type Subscription,
} from "@dlr-shepard/shepard-client";
import { ref, type Ref } from "vue";

const subscriptionToCreate: Ref<Subscription> = ref({
  name: "",
  subscribedURL: "",
  requestMethod: SubscriptionRequestMethodEnum.Get,
});
const requestMethods = Object.values(SubscriptionRequestMethodEnum);

function initSubscription() {
  subscriptionToCreate.value = {
    name: "",
    subscribedURL: "",
    requestMethod: SubscriptionRequestMethodEnum.Get,
  };
}
</script>

<template>
  <b-modal
    id="create-subscription-modal"
    title="Create Subscription"
    @show="initSubscription"
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
