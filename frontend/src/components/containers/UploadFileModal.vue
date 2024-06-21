<script setup lang="ts">
import { ref } from "vue";

defineProps({
  modalId: {
    type: String,
    default: "FileContainerModal",
  },
  modalName: {
    type: String,
    default: "FileContainerModal",
  },
});

const emit = defineEmits(["created"]);

const newFile = ref<Blob>();

function handlePrepare() {
  newFile.value = undefined;
}

function handleOk() {
  emit("created", newFile.value);
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
        v-model="newFile"
        variant="primary"
        placeholder="Upload File"
      >
      </b-form-file>
    </b-modal>
  </div>
</template>
