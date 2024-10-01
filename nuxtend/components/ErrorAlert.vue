<script setup>
import { useEventBus } from "@vueuse/core";
const errorSituation = ref("");
const errorException = ref("");
const errorMessage = ref("");
const errorAlert = ref(false);
const bus = useEventBus(errorKey);
bus.on(e => {
  errorSituation.value = e.situation;
  errorException.value = e.error.exception;
  errorMessage.value = e.error.message;
  errorAlert.value = true;
});
</script>

<template>
  <v-alert
    v-if="errorAlert"
    :text="errorMessage"
    :title="`Error while ${errorSituation}: ${errorException}`"
    type="error"
  />
</template>
