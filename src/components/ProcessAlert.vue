<script setup lang="ts">
import { ref } from "vue";

const emit = defineEmits([
  "success-message-dismissed",
  "error-message-dismissed",
]);

const props = defineProps({
  processName: {
    type: String,
    default: "Process",
  },
  processActive: {
    type: Boolean,
    default: false,
  },
  processFinished: {
    type: Boolean,
    default: false,
  },
  processError: {
    type: Boolean,
    default: false,
  },
});

const timeDelay = 5;
const dismissCountDown = ref(timeDelay);

function successMessageDismissed() {
  emit("success-message-dismissed");
  dismissCountDown.value = timeDelay;
}

function countDownChanged(countDown: number) {
  dismissCountDown.value = countDown;
  if (dismissCountDown.value == 0) successMessageDismissed();
}
</script>

<template>
  <div>
    <b-alert
      :show="props.processActive"
      variant="success"
      class="d-flex align-items-center"
    >
      {{ props.processName }} started. Depending on the size of the file this
      may take a while.
      <b-spinner class="ml-auto" small type="grow"></b-spinner>
    </b-alert>
    <b-alert
      :show="props.processError"
      variant="danger"
      dismissible
      class="d-flex align-items-center"
      @dismissed="emit('error-message-dismissed')"
    >
      {{ props.processName }} failed
    </b-alert>
    <b-alert
      :show="props.processFinished && dismissCountDown"
      variant="success"
      dismissible
      class="d-flex align-items-center"
      @dismissed="successMessageDismissed"
      @dismiss-count-down="countDownChanged"
    >
      {{ props.processName }}. This message will dismiss after {{ dismissCountDown }} seconds...
    </b-alert>
  </div>
</template>
