<script setup lang="ts">
import {
  type RequestMethod,
  type ResponseError,
  SubscriptionApi,
} from "@dlr-shepard/backend-client";
import AddSubscriptionDialog from "~/components/context/user/AddSubscriptionDialog.vue";

const props = defineProps<{ username: string }>();

const showCreateDialog = ref(false);
const subscriptionApi = createApiInstance(SubscriptionApi);
const emit = defineEmits(["created"]);

async function createSubscription(
  name: string,
  callbackUrl: string,
  subscribedUrl: string,
  requestMethod: RequestMethod,
) {
  try {
    await subscriptionApi.createSubscription({
      username: props.username,
      subscription: {
        name: name,
        callbackURL: callbackUrl,
        subscribedURL: subscribedUrl,
        requestMethod: requestMethod,
      },
    });
    emit("created");
    emitSuccess(`Successfully created subscription ${name}!`);
  } catch (e) {
    handleError(e as ResponseError, "creating subscription");
  }
}
</script>

<template>
  <ExpansionPanelTitleButton
    icon="mdi-plus-circle"
    text="ADD"
    @click="showCreateDialog = true"
  />
  <AddSubscriptionDialog
    v-if="showCreateDialog"
    v-model:show-dialog="showCreateDialog"
    @submit="createSubscription"
  />
</template>

<style scoped lang="scss"></style>
