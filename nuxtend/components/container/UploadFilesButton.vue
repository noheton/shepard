<script setup lang="ts">
export interface UploadFilesButtonProps {
  accept?: string;
  buttonText?: string;
  dialogTitle?: string;
  filter?: (files: File[]) => File[];
  maxWidth?: number;
  multiple?: boolean;
  uploadFile: (file: File) => Promise<void>;
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
