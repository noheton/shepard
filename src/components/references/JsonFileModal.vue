<script setup lang="ts">
import JsonEditor from "@/components/generic/JsonEditor.vue";
import FileService from "@/services/fileService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";

import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "JsonFileModal",
  },
  modalName: {
    type: String,
    default: "JsonFileModal",
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

const payload = ref<string>();
const error = ref<boolean>(false);
function retrievePayload() {
  error.value = false;
  FileService.getFile({
    fileContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      response
        .text()
        .then(value => {
          payload.value = value;
        })
        .catch(() => {
          payload.value = "";
          error.value = true;
        });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file");
      error.value = true;
    });
}
</script>

<template>
  <div>
    <b-alert :show="error" variant="danger">File not found</b-alert>
    <b-modal
      :id="modalId"
      ref="modal"
      :title="modalName"
      size="lg"
      lazy
      ok-only
      @show="retrievePayload()"
    >
      <JsonEditor v-if="payload" v-model="payload" read-only />
    </b-modal>
  </div>
</template>
