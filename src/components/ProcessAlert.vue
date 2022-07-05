<template>
  <div>
    <b-alert
      v-model="thisProcessStarted"
      variant="success"
      dismissible
      class="d-flex align-items-center"
      @dismissed="$emit('process-message-dismissed')"
    >
      {{ processName }} started. Depending on the size of the file this may take
      a while.
      <b-spinner
        :hidden="!thisProcessActive"
        class="ml-auto"
        small
        type="grow"
      ></b-spinner>
    </b-alert>
    <b-alert
      v-model="thisProcessError"
      variant="danger"
      dismissible
      class="d-flex align-items-center"
      @dismissed="$emit('error-message-dismissed')"
    >
      {{ processName }} failed
    </b-alert>
  </div>
</template>

<script lang="ts">
import { defineComponent } from "vue";

interface ProcessAlertData {
  thisProcessStarted: boolean;
  thisProcessActive: boolean;
  thisProcessError: boolean;
}

export default defineComponent({
  props: {
    processName: {
      type: String,
      default: "Process",
    },
    processStarted: {
      type: Boolean,
      default: false,
    },
    processActive: {
      type: Boolean,
      default: false,
    },
    processError: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      thisProcessStarted: false,
      thisProcessActive: false,
      thisProcessError: false,
    } as ProcessAlertData;
  },
  watch: {
    processStarted() {
      this.thisProcessStarted = this.processStarted;
    },
    processActive() {
      this.thisProcessActive = this.processActive;
    },
    processError() {
      this.thisProcessError = this.processError;
    },
  },
});
</script>
