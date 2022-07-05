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
          <b><GenericName :name="subscription.name" :word-count="60" /></b> ID:
          {{ subscription.id }} | {{ subscription.callbackURL }} |
          {{ subscription.subscribedURL }} | {{ subscription.requestMethod }}
          <b-button
            v-b-modal.delete-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            class="float-right"
            size="sm"
            variant="dark"
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
      @confirmation="handleDelete(currentSubscription.id)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import GenericName from "@/components/generic/GenericName.vue";
import SubscriptionModal from "@/components/user/SubscriptionModal.vue";
import SubscriptionService from "@/services/subscriptionService";
import { emitter } from "@/utils/event-bus";
import {
  Subscription,
  SubscriptionRequestMethodEnum,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface SubscriptionListData {
  subscriptions: Subscription[];
  currentSubscription?: Subscription;
  requestMethods: string[];
}

export default defineComponent({
  components: { DeleteConfirmationModal, SubscriptionModal, GenericName },
  props: {
    currentUsername: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      subscriptions: new Array<Subscription>(),
      currentSubscription: undefined,
      requestMethods: Object.values(SubscriptionRequestMethodEnum),
    } as SubscriptionListData;
  },
  mounted() {
    this.retrieveSubscriptions();
  },
  methods: {
    retrieveSubscriptions() {
      if (!this.currentUsername) return;
      SubscriptionService.getAllSubscriptions({
        username: this.currentUsername,
      })
        .then(response => {
          this.subscriptions = response;
        })
        .catch(e => {
          const error = "Error while fetching subscriptions: " + e.statusText;
          console.log(error);
        });
    },
    handleCreate(subscription: Subscription) {
      SubscriptionService.createSubscription({
        username: this.currentUsername,
        subscription: subscription,
      })
        .then(response => {
          this.subscriptions.push(response);
        })
        .catch(e => {
          const error = "Error while creating subscription: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    handleDelete(id: number) {
      SubscriptionService.deleteSubscription({
        username: this.currentUsername,
        subscriptionId: id,
      })
        .catch(e => {
          const error = "Error while deleting subscription: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        })
        .finally(() => {
          this.currentSubscription = undefined;
          this.retrieveSubscriptions();
        });
    },
  },
});
</script>
