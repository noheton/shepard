<script setup lang="ts">
import type { FileType } from "./ShepardFileDataTableItem";

interface FileContentViewerDialogProps {
  collectionId: number;
  dataObjectId: number;
  fileReferenceId: number;
  oid: string;
  fileType: FileType;
}
defineProps<FileContentViewerDialogProps>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});
</script>

<template>
  <ImageViewerDialog
    v-if="showDialog && fileType === 'image'"
    v-model:show-dialog="showDialog"
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    :file-reference-id="fileReferenceId"
    :oid="oid"
  />
  <TextViewerDialog
    v-if="showDialog && fileType === 'text'"
    v-model:show-dialog="showDialog"
    :collection-id="collectionId"
    :data-object-id="dataObjectId"
    :file-reference-id="fileReferenceId"
    :oid="oid"
  />
</template>
