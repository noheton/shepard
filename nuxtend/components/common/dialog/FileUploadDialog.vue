<script setup lang="ts">
interface FileUploadDialogProps {
  uploadFile: (file: File) => Promise<void>;
  maxWidth?: number;
  multiple?: boolean;
  title?: string;
}

const props = withDefaults(defineProps<FileUploadDialogProps>(), {
  multiple: true,
  maxWidth: 800,
  title: "File Upload",
});

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const files = ref<File | File[] | undefined>(undefined);
const isUploadButtonDisabled = ref<boolean>(true);
const uploading = ref<boolean>(false);

// Disable Upload button if no file is selected
watch(files, () => {
  isUploadButtonDisabled.value =
    files.value === undefined ||
    (Array.isArray(files.value) && files.value.length === 0);
});

const uploadFiles = async () => {
  uploading.value = true;
  if (files.value === undefined) return;
  if (files.value instanceof File) files.value = [files.value];
  if (Array.isArray(files.value)) {
    await Promise.all(files.value.map(file => props.uploadFile(file)))
      .then(() => {
        emitSuccess("All files uploaded successfully");
        showDialog.value = false;
      })
      .catch(_ => {
        handleError(
          new Error(
            "At least one file upload failed. Please check the file list.",
          ),
          "uploading files",
        );
      })
      .finally(() => {
        uploading.value = false;
      });
  }
};
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="maxWidth">
    <v-card color="canvas" :loading="uploading">
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4 text-wrap">{{ title }}</div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>
      <template #text>
        <slot name="text" />
      </template>
      <div class="px-6">
        <v-file-upload
          v-model:model-value="files"
          density="compact"
          icon="mdi-folder-upload-outline"
          :multiple="multiple"
          clearable
          show-size
          color="canvas"
          title="Drag and drop files here (or click to browse)"
        />
      </div>
      <template #actions>
        <slot name="actions">
          <!-- Default if no slot content for actions is provided -->
          <v-row justify="end">
            <v-spacer />
            <v-col cols="auto">
              <v-btn
                color="treeview"
                variant="flat"
                @click="showDialog = false"
              >
                Cancel
              </v-btn>
              <v-btn
                color="primary"
                variant="flat"
                class="ml-4"
                :disabled="isUploadButtonDisabled"
                @click="
                  () => {
                    uploadFiles();
                  }
                "
              >
                Upload
              </v-btn>
            </v-col>
          </v-row>
        </slot>
      </template>
    </v-card>
  </v-dialog>
</template>

<style lang="scss" scoped>
:deep(.v-file-upload) {
  border-color: rgb(var(--v-theme-medium-emphasis)) !important;
  //background-color: rgb(var(--v-theme-canvas)) !important;
  // background-color: rgb(var(--v-theme-medium-emphasis)) !important;
  // color: rgb(var(--v-theme-textbody1)) !important;
}

:deep(.v-file-upload-title) {
  color: rgb(var(--v-theme-textbody1)) !important;
}

:deep(.v-list-item) {
  border-color: rgb(var(--v-theme-textbody1)) !important;
}
</style>
