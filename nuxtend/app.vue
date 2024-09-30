<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <v-snackbar
    v-model="errorAlert"
    location="right"
    timeout="-1"
    max-height="80px"
  >
    Error while {{ errorSituation }}: {{ errorException }} and come other text
    to make it long
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
