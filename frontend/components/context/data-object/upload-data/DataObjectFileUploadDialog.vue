<script lang="ts" setup>
import { useDisplay } from "vuetify";
import {
  ContainerType,
  PermissionType,
  type Collection,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";
import { useFileUploadProgress } from "~/composables/container/useFileUploadProgress";
import { UploadAbortError } from "~/composables/container/xhrUpload";
import { CollectionAccessor } from "~/composables/context/CollectionAccessor";
import { useCreateFileContainer } from "~/composables/data/useCreateFileContainer";
import { useCreateFileReference } from "~/composables/references/useCreateFileReference";
import type { FileRef } from "~/components/context/data-references/create-dialog/DataRef";

interface FileUploadDialogProps {
  collectionId: number;
  dataobjectId: number;
  createReference: boolean;
  accept?: string;
}

const props = withDefaults(defineProps<FileUploadDialogProps>(), {
  accept: "*",
});
const emit = defineEmits<{
  (e: "files-uploaded", files: ShepardFile[], containerId: number): void;
}>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const files = defineModel<File[]>("files", {
  required: false,
  default: [],
});

const { addFileReference } = useCreateFileReference(
  props.collectionId,
  props.dataobjectId,
  () => {},
);

const collectionAccessor = new CollectionAccessor(props.collectionId);
try {
  await collectionAccessor.fetchData();
} catch {
  handleError("Could not initialize collection values", "file upload");
}

const fileContainerId = ref<number | undefined>(undefined);
let containerAccessor: FileContainerAccessor | undefined = undefined;

const newFileContainerName = ref<string>("");
const newFileContainerPermissionType = ref<PermissionType>(
  PermissionType.Private,
);

const newReferenceName = ref<string>("");
const isFileContainerDefault = ref<boolean>(false);
const isCreatingNewFileContainer = ref<boolean>(false);

const uploading = ref<boolean>(false);
const successCount = ref<number>(0);
const { mobile } = useDisplay();

// Task #135 — per-batch progress state shared with the FileUploadProgressPanel.
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
    (Array.isArray(files.value) && files.value.length === 0) ||
    (props.createReference && !newReferenceName.value) ||
    (isCreatingNewFileContainer.value === false &&
      fileContainerId.value === undefined) ||
    (isCreatingNewFileContainer.value === true && !newFileContainerName.value)
  );
});

if (
  collectionAccessor.collection.value &&
  collectionAccessor.collection.value.defaultFileContainerId
) {
  fileContainerId.value =
    collectionAccessor.collection.value.defaultFileContainerId;
}

async function createFileReferences(files: (ShepardFile | undefined)[]) {
  if (!containerAccessor) {
    return;
  }
  if (!fileContainerId.value) {
    return;
  }
  const fileRef: FileRef = {
    fileOids: files
      .filter(entry => entry !== undefined)
      .filter(file => file.oid !== undefined)
      .map(file => file.oid!),
  };
  try {
    await addFileReference(
      newReferenceName.value,
      fileContainerId.value,
      fileRef,
    );
  } catch {
    handleError("Could not create file reference", "upload files");
  }
}

async function createNewFileContainer() {
  if (isCreatingNewFileContainer.value === true) {
    try {
      const newFileContainer = await useCreateFileContainer(
        newFileContainerName.value,
        newFileContainerPermissionType.value,
      );
      if (newFileContainer) {
        fileContainerId.value = newFileContainer?.id;
      }
    } catch {
      handleError("Could not create new file container", "file upload");
    }
  }
}

async function updateDefaultFileContainer() {
  if (
    isFileContainerDefault.value === true &&
    collectionAccessor.collection.value
  ) {
    const collection: Collection = {
      ...collectionAccessor.collection.value,
      defaultFileContainerId: fileContainerId.value,
    };
    try {
      await collectionAccessor.updateCollection(collection);
    } catch {
      handleError("Could not change default file container", "upload file");
    }
  }
}

async function uploadFileWithProgress(
  file: File,
  index: number,
  signal: AbortSignal,
): Promise<ShepardFile> {
  if (!fileContainerId.value) {
    throw new Error("File container needs to be set!");
  }
  if (!containerAccessor) {
    containerAccessor = new FileContainerAccessor(fileContainerId.value);
  }
  progress.markStarted(index);
  try {
    const uploadedShepardFile = await containerAccessor.uploadFile(file, {
      onProgress: ev => {
        progress.reportProgress(index, ev.bytesUploaded, ev.bytesTotal);
      },
      signal,
    });
    progress.markDone(index);
    successCount.value += 1;
    return uploadedShepardFile;
  } catch (e) {
    if (e instanceof UploadAbortError || signal.aborted) {
      progress.markCancelled(index);
    } else {
      progress.markError(index, (e as Error).message ?? "Upload failed");
    }
    throw e;
  }
}

async function handleFileUpload(): Promise<ShepardFile[]> {
  if (files.value.length === 0) throw new Error("No files selected!");
  const signal = progress.startBatch(files.value);
  let uploadedFiles: ShepardFile[] = [];
  try {
    const results = await Promise.allSettled(
      files.value.map((f, i) => uploadFileWithProgress(f, i, signal)),
    );
    uploadedFiles = results
      .filter(
        (r): r is PromiseFulfilledResult<ShepardFile> => r.status === "fulfilled",
      )
      .map(r => r.value);
    const failures = results.length - uploadedFiles.length;
    if (signal.aborted) {
      handleError(
        `Upload cancelled — ${uploadedFiles.length} of ${results.length} files completed before cancel.`,
        "uploading files",
      );
    } else if (failures === 0) {
      emitSuccess(`${successCount.value} file(s) uploaded successfully.`);
    } else {
      handleError(
        `Only ${uploadedFiles.length} of ${results.length} files uploaded successfully.`,
        "uploading files",
      );
    }
  } finally {
    progress.finishBatch();
  }
  return uploadedFiles;
}

async function handleUserSubmit() {
  uploading.value = true;

  try {
    await createNewFileContainer();
    const uploadedFiles = await handleFileUpload();
    if (!uploadedFiles || !uploadedFiles.values()) {
      throw Error("Error in file upload");
    }

    if (props.createReference) {
      await createFileReferences(uploadedFiles);
    }
    await updateDefaultFileContainer();
    emit("files-uploaded", uploadedFiles, fileContainerId.value!);
  } catch {
    handleError("Could not upload files", "file upload");
  }

  uploading.value = false;
  showDialog.value = false;
}

function updateReferenceNameByFileName() {
  if (files.value === undefined) return;
  if (files.value instanceof File) return;
  if (Array.isArray(files.value)) {
    if (files.value.length === 1) {
      newReferenceName.value = `${currentDateShortForm()}-${files.value[0]!.name}`;
    } else {
      newReferenceName.value = "";
    }
  }
}

updateReferenceNameByFileName();

watch(
  files,
  () => {
    updateReferenceNameByFileName();
  },
  { deep: true },
);

// CC1c: pre-fill the container name with "<Collection name> — file store" when
// the user toggles "Create new file container" on. Only fills when the field is
// still blank so the user can type their own name without it being overwritten.
watch(isCreatingNewFileContainer, (creating: boolean) => {
  if (creating && !newFileContainerName.value) {
    const collectionName = collectionAccessor.collection.value?.name ?? "";
    if (collectionName) {
      newFileContainerName.value = `${collectionName} — file store`;
    }
  }
});
</script>

<template>
  <v-dialog v-model="showDialog" :max-width="900" persistent :fullscreen="mobile">
    <v-card :loading="uploading" color="canvas">
      <template #title>
        <div class="mb-8 text-h4 text-wrap">Upload Files</div>
      </template>
      <template #text>
        <div class="d-flex flex-column ga-4">
          <!-- CC1c: first-time explanation of the Collection / Container duality -->
          <v-alert
            type="info"
            variant="tonal"
            density="compact"
            class="mb-2"
            icon="mdi-information-outline"
          >
            Files you add here are stored in a secure container linked to this
            dataset. You can link the same data to multiple experiments without
            copying it.
          </v-alert>

          <v-file-upload
            v-if="!uploading"
            v-model:model-value="files"
            :accept="accept"
            :multiple="true"
            class="mb-6"
            clearable
            density="compact"
            icon="mdi-folder-upload-outline"
            show-size
            title="Drag and drop files here (or click to browse)"
          />

          <!-- Task #135 — progress panel during upload -->
          <FileUploadProgressPanel
            v-if="uploading"
            :items="progress.items.value"
            :aggregate="progress.aggregate.value"
            :can-cancel="isAnyFileActive"
            @cancel="progress.cancel()"
          />

          <div v-if="!uploading" class="d-flex flex-column ga-1">
            <div class="d-flex justify-space-between align-center">
              <span class="text-textbody text-subtitle-2">
                Storage Location
              </span>
              <v-switch
                v-model:model-value="isCreatingNewFileContainer"
                color="primary"
                density="compact"
                flat
                hide-details
                label="Create new file container"
              />
            </div>
            <ContainerInput
              v-if="!isCreatingNewFileContainer"
              v-model:container-id="fileContainerId"
              :collection-id="collectionId"
              :container-type="ContainerType.File"
              :is-required="true"
              @container-selected="
                (id, _) => {
                  fileContainerId = id;
                }
              "
              @selection-cleared="fileContainerId = undefined"
            />

            <div v-else class="d-flex flex-column ga-4">
              <SimpleInput
                v-model:input-string="newFileContainerName"
                label="New File Container Name"
              />
              <PermissionTypeInput
                v-model:permission-type="newFileContainerPermissionType"
              />
            </div>

            <v-checkbox
              v-if="
                collectionAccessor.collection.value?.defaultFileContainerId !==
                  fileContainerId || isCreatingNewFileContainer
              "
              v-model="isFileContainerDefault"
              color="primary"
              density="compact"
              label="Set as new default file container"
            />
          </div>

          <div v-if="createReference && !uploading" class="d-flex flex-column ga-2">
            <div class="d-flex ga-2 align-center">
              <span class="text-textbody text-subtitle-2">Reference Name</span>
              <Tooltip>
                <div>
                  This is the name that will appear in your list of file
                  references.
                </div>
              </Tooltip>
            </div>

            <SimpleInput v-model:input-string="newReferenceName" label="Name" />
          </div>

          <div v-if="!uploading" class="mt-6">
            <span>* Required fields</span>
          </div>
        </div>
      </template>

      <template #actions>
        <slot name="actions">
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
                :disabled="isUploadButtonDisabled"
                class="ml-4"
                color="primary"
                variant="flat"
                @click="handleUserSubmit"
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
