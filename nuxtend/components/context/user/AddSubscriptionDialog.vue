<script lang="ts" setup>
import { RequestMethod } from "@dlr-shepard/backend-client";

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
const name = ref("");
const callbackUrl = ref("");
const subscribedUrl = ref("");
const requestMethod = ref(RequestMethod.Get);
const emit = defineEmits<{
  (
    e: "submit",
    name: string,
    callbackUrl: string,
    subscribedUrl: string,
    requestMethod: RequestMethod,
  ): void;
}>();

const isValid = computed(() => {
  return (
    name.value.length > 0 &&
    callbackUrl.value.length > 0 &&
    subscribedUrl.value.length > 0
  );
});
</script>

<template>
  <FormDialog
    v-if="showDialog"
    v-model:show-dialog="showDialog"
    :close-on-submit="true"
    :submit-disabled="!isValid"
    save-button-text="Create"
    title="Create Subscription"
    @submit="emit('submit', name, callbackUrl, subscribedUrl, requestMethod)"
  >
    <template #form>
      <div class="wrapper mt-8">
        <SimpleInput v-model:input-string="name" label="Name" />
        <SimpleInput v-model:input-string="callbackUrl" label="Callback URL" />
        <SimpleInput
          v-model:input-string="subscribedUrl"
          label="Subscribed URL"
        />
        <Select
          v-model:model-value="requestMethod"
          :items="Object.entries(RequestMethod)"
          density="compact"
          item-title="1"
          label="Request method*"
          variant="outlined"
        />
      </div>
    </template>
  </FormDialog>
</template>

<style lang="scss" scoped>
.wrapper {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
</style>
