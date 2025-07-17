<script setup lang="ts">
import { FileReferenceApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

interface ImageViewerDialogProps {
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  oid: string;
}

const props = defineProps<ImageViewerDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

function loadImageFile() {
  useShepardApi(FileReferenceApi)
    .value.getFilePayload({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      fileReferenceId: props.fileReferenceId,
      oid: props.oid,
    })
    .then(response => {
      showFile(response);
    })
    .catch(e => {
      handleError(e, "loading image data");
    });
}

const imageUrl = ref<string>();
function showFile(response: Blob) {
  // Release the old blob object if it exists
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value);

  imageUrl.value = URL.createObjectURL(response);
}

loadImageFile();
</script>

<template>
  <InformationDialog v-model:show-dialog="showDialog" title="Image Content">
    <template #text>
      <v-img :src="imageUrl" max-height="400px" />
    </template>
  </InformationDialog>
</template>
