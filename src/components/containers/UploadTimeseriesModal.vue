<script setup lang="ts">
import { ref } from "vue";

defineProps({
  modalId: {
    type: String,
    default: "UploadTimeseriesModal",
  },
  modalName: {
    type: String,
    default: "uploaded",
  },
});

const emit = defineEmits(["uploaded"]);

const newTimeseriesFile = ref<Blob>();

function handlePrepare() {
  newTimeseriesFile.value = undefined;
}

function handleOk() {
  emit("uploaded", newTimeseriesFile.value);
}
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      lazy
      @show="handlePrepare()"
      @ok="handleOk()"
    >
      <b-form-file
        v-model="newTimeseriesFile"
        variant="primary"
        placeholder="Select Timeseries File"
      >
      </b-form-file>
    </b-modal>
  </div>
</template>
