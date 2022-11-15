<script setup lang="ts">
import FileService from "@/services/fileService";
import { logError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "TextViewerModal",
  },
  modalName: {
    type: String,
    default: "Text Viewer Modal",
  },
  containerId: {
    type: Number,
    required: true,
  },
  oid: {
    type: String,
    required: true,
  },
});

const text = ref("");
const error = ref(false);
function retrievePayload() {
  text.value = "";
  error.value = false;
  FileService.getFile({
    fileContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      response
        .text()
        .then(value => {
          text.value = value;
        })
        .catch(() => {
          text.value = "";
          error.value = true;
        });
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      text.value = "";
      error.value = true;
    });
}
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      :title="modalName"
      size="lg"
      lazy
      ok-only
      @show="retrievePayload()"
    >
      <b-alert :show="error" variant="danger">File not found</b-alert>
      <div>
        <b-form-textarea
          v-model="text"
          plaintext
          max-rows="20"
        ></b-form-textarea>
      </div>
    </b-modal>
  </div>
</template>
