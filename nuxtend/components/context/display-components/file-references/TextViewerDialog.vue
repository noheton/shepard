<script setup lang="ts">
import { FileReferenceApi } from "@dlr-shepard/backend-client";
import InformationDialog from "~/components/common/dialog/InformationDialog.vue";
import { mapFileNameToCodeType } from "./shepardFileMappingUtil";

interface TextViewerDialogProps {
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  oid: string;
  fileName: string;
}
const props = defineProps<TextViewerDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const textContent = ref<string>("");

function loadTextFile() {
  createApiInstance(FileReferenceApi)
    .getFilePayload({
      collectionId: props.collectionId,
      dataObjectId: props.dataObjectId,
      fileReferenceId: props.fileReferenceId,
      oid: props.oid,
    })
    .then(response => {
      response
        .text()
        .then(value => {
          textContent.value = value;
        })
        .catch(error => {
          handleError(error, "Loading text content");
        });
    })
    .catch(e => {
      handleError(e, "loading image data");
    });
}

loadTextFile();
</script>

<template>
  <InformationDialog
    v-model:show-dialog="showDialog"
    :max-width="1000"
    title="Text File Content"
  >
    <template #text>
      <RichTextEditor
        v-model="textContent"
        :is-editable="false"
        :code-type="mapFileNameToCodeType(fileName)"
      />
    </template>
  </InformationDialog>
</template>
