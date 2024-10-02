<script setup>
import { useEventBus } from "@vueuse/core";
const errorSituation = ref("");
const errorException = ref("");
const errorMessage = ref("");
const errorAlert = defineModel({ type: Boolean, default: false });
const bus = useEventBus(errorKey);
bus.on(e => {
  errorSituation.value = e.situation;
  errorException.value = e.error.exception;
  errorMessage.value = e.error.message;
  errorAlert.value = true;
});
</script>

<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <v-snackbar v-model="errorAlert" timeout="-1" max-height="80px">
    Error while {{ errorSituation }}: {{ errorException }}
    <br />
    <small>
      <i>{{ errorMessage }}</i>
    </small>
    <template #actions>
      <v-btn color="pink" variant="text" @click="errorAlert = false">
        Close
      </v-btn>
    </template>
  </v-snackbar>
</template>
