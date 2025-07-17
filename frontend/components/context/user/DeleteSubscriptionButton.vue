<script setup lang="ts">
import {
  type ResponseError,
  SubscriptionApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const props = defineProps<{
  username: string;
  subscriptionId: number;
}>();
const showDeleteDialog = ref(false);
const emit = defineEmits(["deleted"]);

const subscriptionApi = useShepardApi(SubscriptionApi);

async function deleteSubscription() {
  try {
    await subscriptionApi.value.deleteSubscription({
      username: props.username,
      subscriptionId: props.subscriptionId,
    });
    emit("deleted");
    emitSuccess(`Successfully deleted subscription!`);
  } catch (e) {
    handleError(e as ResponseError, "deleting subscription");
  }
}
</script>

<template>
  <ActionButton icon="mdi-delete-outline" @click="showDeleteDialog = true" />
  <ConfirmDeleteDialog
    v-if="showDeleteDialog"
    v-model:show-dialog="showDeleteDialog"
    @confirmed="deleteSubscription"
  />
</template>

<style scoped lang="scss"></style>
