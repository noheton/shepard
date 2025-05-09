<script setup lang="ts">
interface FileUploadDialogProps {
  accept: string;
  filter: (files: File[]) => File[];
  maxWidth: number;
  multiple: boolean;
  title: string;
  uploadFile: (file: File) => Promise<void>;
}

const props = defineProps<FileUploadDialogProps>();

const emits = defineEmits<{
  (e: "uploadFinished"): void;
}>();

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const files = ref<File | File[] | undefined>(undefined);
const uploading = ref<boolean>(false);
const successCount = ref<number>(0);
const errorCount = ref<number>(0);
const isUploadButtonDisabled = computed(() => {
  return (
    files.value === undefined ||
    (Array.isArray(files.value) && files.value.length === 0)
  );
});

const filterFiles = (selectedFiles: File[]): void => {
  files.value = props.filter(selectedFiles);
};

const uploadFile = async (file: File) => {
  try {
    await props.uploadFile(file);
    successCount.value += 1;
  } catch {
    errorCount.value += 1;
  }
};

const uploadFiles = async () => {
  uploading.value = true;
  if (files.value === undefined) return;
  if (files.value instanceof File) files.value = [files.value];
  if (Array.isArray(files.value)) {
    await Promise.all(files.value.map(uploadFile));
    if (errorCount.value === 0)
      emitSuccess(`${successCount.value} file(s) uploaded successfully.`);
    else {
      const numberOfFiles = (files.value as Array<File>).length;
      handleError(
        `${successCount.value} of ${numberOfFiles} files uploaded successfully.`,
        "uploading files",
      );
    }
    emits("uploadFinished");
    uploading.value = false;
    showDialog.value = false;
  }
};
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="maxWidth">
    <v-card color="canvas" :loading="uploading">
      <template #title>
        <div class="pb-4 d-flex justify-space-between align-baseline">
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
        <div
          v-if="$slots.info"
          class="mb-4 pa-4 bg-divider2 rounded text-body-1"
        >
          <slot name="info" />
        </div>
        <v-file-upload
          v-model:model-value="files"
          :accept="accept"
          clearable
          density="compact"
          icon="mdi-folder-upload-outline"
          :multiple="multiple"
          show-size
          title="Drag and drop files here (or click to browse)"
          @update:model-value="filterFiles"
        />
      </template>

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
                @click="uploadFiles"
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
}

:deep(.v-file-upload-title) {
  color: rgb(var(--v-theme-textbody1)) !important;
}

:deep(.v-list-item) {
  border-color: rgb(var(--v-theme-textbody1)) !important;
}
</style>
