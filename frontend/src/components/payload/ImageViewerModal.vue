<script setup lang="ts">
import FileService from "@/services/fileService";
import { logError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { ref } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "ImageViewerModal",
  },
  modalName: {
    type: String,
    default: "Image Viewer Modal",
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

const error = ref(false);
function retrievePayload() {
  error.value = false;
  FileService.getFile({
    fileContainerId: props.containerId,
    oid: props.oid,
  })
    .then(response => {
      showFile(response);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      error.value = true;
    });
}

const objectURL = ref<string>();
function showFile(response: Blob | null) {
  if (objectURL.value) URL.revokeObjectURL(objectURL.value);
  const myImage: HTMLImageElement | null = document.querySelector("#image");
  if (!myImage) return; // image element not found, ignoring
  if (response) {
    objectURL.value = URL.createObjectURL(response);
    myImage.src = objectURL.value;
  } else {
    myImage.src = "";
  }
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
      <div>
        <b-img id="image" fluid />
      </div>
    </b-modal>
  </div>
</template>
