<script setup lang="ts">
import Breadcrumb from "@/components/Breadcrumb.vue";
import Navbar from "@/components/Navbar.vue";
import { errorKey } from "@/utils/event-bus";
import { useEventBus } from "@vueuse/core";
import { ref } from "vue";

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
  <div id="app">
    <Navbar />
    <b-alert
      :show="errorAlert"
      dismissible
      variant="danger"
      @dismissed="errorAlert = false"
    >
      Error while {{ errorSituation }}: <b>{{ errorException }}</b>
      <br />
      <small>
        <i>{{ errorMessage }}</i>
      </small>
    </b-alert>
    <Breadcrumb class="view" />
    <router-view :key="$route.fullPath" class="mt-4" />
  </div>
</template>
