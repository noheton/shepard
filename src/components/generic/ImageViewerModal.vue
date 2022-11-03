<script setup lang="ts">
import FileService from "@/services/fileService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "JsonEditorModal",
  },
  modalName: {
    type: String,
    default: "JsonEditorModal",
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

function retrievePayload() {
  FileService.getFile({
    fileContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      showFile(response);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file payload");
    });
}

const objectURL = ref<string>();
function showFile(response: Blob) {
  if (objectURL.value) URL.revokeObjectURL(objectURL.value);
  objectURL.value = URL.createObjectURL(response);
  const myImage: (Element & { src: string }) | null =
    document.querySelector("#image");
  if (myImage != null) myImage.src = objectURL.value;
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
      <div>
        <b-img id="image" fluid />
      </div>
    </b-modal>
  </div>
</template>
