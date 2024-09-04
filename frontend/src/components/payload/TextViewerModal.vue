<script setup lang="ts">
import TextEditor from "@/components/generic/TextEditor.vue";
import type { ResponseError } from "@/generated/openapi";
import FileService from "@/services/fileService";
import { logError } from "@/utils/error-handling";
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
      <b-alert :show="error" variant="danger">File not available</b-alert>
      <TextEditor v-model="text" read-only></TextEditor>
    </b-modal>
  </div>
</template>
