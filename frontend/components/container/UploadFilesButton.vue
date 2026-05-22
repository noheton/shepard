<script setup lang="ts">
import type { XhrUploadOptions } from "~/composables/container/xhrUpload";

export interface UploadFilesButtonProps {
  accept?: string;
  buttonText?: string;
  dialogTitle?: string;
  filter?: (files: File[]) => File[];
  maxWidth?: number;
  multiple?: boolean;
  /**
   * Task #135 — `options` (containing `onProgress`/`signal`) is optional so
   * callers that don't care about progress keep working unchanged.  Callers
   * that DO care forward the options to `FileContainerAccessor.uploadFile`
   * (or their own XHR-based upload) to surface progress + cancel.
   */
  uploadFile: (file: File, options?: XhrUploadOptions) => Promise<void>;
}

const emits = defineEmits(["upload-finished"]);

const showFileUploadDialog = ref<boolean>(false);
withDefaults(defineProps<UploadFilesButtonProps>(), {
  accept: "",
  buttonText: "Upload Files",
  dialogTitle: "Upload Files",
  filter: (files: File[]) => files,
  maxWidth: 800,
  multiple: true,
});
</script>

<template>
  <v-btn
    color="primary"
    variant="flat"
    prepend-icon="mdi-tray-arrow-up"
    :text="buttonText"
    @click="showFileUploadDialog = true"
  />
  <FileUploadDialog
    v-if="showFileUploadDialog"
    v-model:show-dialog="showFileUploadDialog"
    :accept="accept"
    :filter="filter"
    :max-width="maxWidth"
    :multiple="multiple"
    :title="dialogTitle"
    :upload-file="uploadFile"
    @upload-finished="emits('upload-finished')"
  >
    <template v-if="$slots.info" #info>
      <slot name="info" />
    </template>
  </FileUploadDialog>
</template>
