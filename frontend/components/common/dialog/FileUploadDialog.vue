<script setup lang="ts">
import { useDisplay } from "vuetify";
import { useFileUploadProgress } from "~/composables/container/useFileUploadProgress";
import {
  UploadAbortError,
  type XhrUploadOptions,
} from "~/composables/container/xhrUpload";

/**
 * Task #135 — `uploadFile` may optionally accept `XhrUploadOptions` so the
 * dialog can wire progress events + a cancel signal through to the actual
 * upload implementation.  Callers that ignore the second arg keep working as
 * before (no progress, no cancel — but the spinner still shows).
 */
interface FileUploadDialogProps {
  accept: string;
  filter: (files: File[]) => File[];
  maxWidth: number;
  multiple: boolean;
  title: string;
  uploadFile: (file: File, options?: XhrUploadOptions) => Promise<void>;
}

const props = defineProps<FileUploadDialogProps>();

const emits = defineEmits(["uploadFinished"]);

const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const files = ref<File | File[] | undefined>(undefined);
const uploading = ref<boolean>(false);
const { mobile } = useDisplay();
const successCount = ref<number>(0);
const errorCount = ref<number>(0);
const progress = useFileUploadProgress();
const isAnyFileActive = computed(() =>
  progress.items.value.some(
    it =>
      it.status === "uploading" ||
      it.status === "indeterminate" ||
      it.status === "pending",
  ),
);
const isUploadButtonDisabled = computed(() => {
  return (
    files.value === undefined ||
    (Array.isArray(files.value) && files.value.length === 0)
  );
});

const filterFiles = (selectedFiles: File[]): void => {
  files.value = props.filter(selectedFiles);
};

const uploadOne = async (file: File, index: number, signal: AbortSignal) => {
  progress.markStarted(index);
  try {
    await props.uploadFile(file, {
      onProgress: ev => {
        progress.reportProgress(index, ev.bytesUploaded, ev.bytesTotal);
      },
      signal,
    });
    progress.markDone(index);
    successCount.value += 1;
  } catch (e) {
    if (e instanceof UploadAbortError || signal.aborted) {
      progress.markCancelled(index);
    } else {
      progress.markError(index, (e as Error).message ?? "Upload failed");
    }
    errorCount.value += 1;
  }
};

const uploadFiles = async () => {
  uploading.value = true;
  if (files.value === undefined) return;
  if (files.value instanceof File) files.value = [files.value];
  if (Array.isArray(files.value)) {
    const list = files.value as File[];
    const signal = progress.startBatch(list);
    try {
      await Promise.all(list.map((f, i) => uploadOne(f, i, signal)));
      if (signal.aborted) {
        handleError(
          `Upload cancelled — ${successCount.value} of ${list.length} files completed before cancel.`,
          "uploading files",
        );
      } else if (errorCount.value === 0) {
        emitSuccess(`${successCount.value} file(s) uploaded successfully.`);
      } else {
        handleError(
          `${successCount.value} of ${list.length} files uploaded successfully.`,
          "uploading files",
        );
      }
    } finally {
      progress.finishBatch();
    }
    emits("uploadFinished");
    uploading.value = false;
    showDialog.value = false;
  }
};
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="maxWidth" :fullscreen="mobile">
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
          v-if="!uploading"
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
        <!-- Task #135 — live progress panel -->
        <FileUploadProgressPanel
          v-if="uploading"
          :items="progress.items.value"
          :aggregate="progress.aggregate.value"
          :can-cancel="isAnyFileActive"
          @cancel="progress.cancel()"
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
                :disabled="uploading"
                @click="showDialog = false"
              >
                Close
              </v-btn>
              <v-btn
                v-if="!uploading"
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
  background-color: rgb(var(--v-theme-canvas));
}

:deep(.v-file-upload-title) {
  color: rgb(var(--v-theme-textbody1)) !important;
}

:deep(.v-list-item) {
  border-color: rgb(var(--v-theme-textbody1)) !important;
}
</style>
