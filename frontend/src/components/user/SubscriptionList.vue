<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import SubscriptionModal from "@/components/user/SubscriptionModal.vue";
import SubscriptionService from "@/services/subscriptionService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError, Subscription } from "@dlr-shepard/shepard-client";
import { onMounted, ref } from "vue";

const props = defineProps({
  currentUsername: {
    type: String,
    required: true,
  },
});

const subscriptions = ref<Subscription[]>([]);
const currentSubscription = ref<Subscription>();

function retrieveSubscriptions() {
  if (!props.currentUsername) return;
  SubscriptionService.getAllSubscriptions({
    username: props.currentUsername,
  })
    .then(response => {
      subscriptions.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching subscriptions");
    });
}

function handleCreate(subscription: Subscription) {
  SubscriptionService.createSubscription({
    username: props.currentUsername,
    subscription: subscription,
  })
    .then(response => {
      subscriptions.value.push(response);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating subscription");
    });
}

function handleDelete() {
  if (!currentSubscription.value?.id) return;
  SubscriptionService.deleteSubscription({
    username: props.currentUsername,
    subscriptionId: currentSubscription.value.id,
  })
    .catch(e => {
      handleError(e as ResponseError, "deleting subscription");
    })
    .finally(() => {
      currentSubscription.value = undefined;
      retrieveSubscriptions();
    });
}
onMounted(() => {
  retrieveSubscriptions();
});
</script>

<template>
  <div>
    <b-container>
      <b-row class="mb-2">
        <b-button
          v-b-modal.create-subscription-modal
          class="ml-auto"
          variant="primary"
        >
          Create
        </b-button>
      </b-row>
    </b-container>
    <div>
      <b-list-group>
        <b-list-group-item
          v-for="(subscription, index) in subscriptions"
          :key="index"
        >
          <b>
            <GenericName :name="subscription.name || ''" :word-count="60" />
          </b>
          ID: {{ subscription.id }} | {{ subscription.callbackURL }} |
          {{ subscription.subscribedURL }} | {{ subscription.requestMethod }}
          <b-button
            v-b-modal.delete-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            class="float-right"
            size="sm"
            variant="info"
            @click="currentSubscription = subscription"
          >
            <DeleteIcon />
          </b-button>
        </b-list-group-item>
      </b-list-group>
    </div>
    <SubscriptionModal @create="handleCreate($event)" />
    <DeleteConfirmationModal
      v-if="currentSubscription"
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete subscription"
      :modal-text="
        'Do you really want do delete the subscription with name ' +
        currentSubscription.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </div>
</template>
