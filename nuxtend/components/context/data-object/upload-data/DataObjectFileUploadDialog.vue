<script setup lang="ts">
import {
  ContainerType,
  PermissionType,
  type Collection,
  type ShepardFile,
} from "@dlr-shepard/backend-client";
import { FileContainerAccessor } from "~/composables/container/FileContainerAccessor";
import { CollectionAccessor } from "~/composables/context/CollectionAccessor";
import { useCreateFileContainer } from "~/composables/data/useCreateFileContainer";
import { useCreateFileReference } from "~/composables/references/useCreateFileReference";
import { currentDateShortForm } from "~/utils/helpers";
import type { FileRef } from "../../data-references/create-dialog/DataRef";

interface FileUploadDialogProps {
  collectionId: number;
  dataobjectId: number;
}

const props = defineProps<FileUploadDialogProps>();
const emits = defineEmits(["uploadFinished"]);
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const files = defineModel<File | File[] | undefined>("files", {
  required: false,
  default: undefined,
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
const errorCount = ref<number>(0);
const isUploadButtonDisabled = computed(() => {
  return (
    files.value === undefined ||
    (Array.isArray(files.value) && files.value.length === 0) ||
    !newReferenceName.value ||
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

async function uploadFile(file: File): Promise<ShepardFile | undefined> {
  if (!fileContainerId.value) {
    errorCount.value += 1;
    return;
  }
  if (!containerAccessor) {
    containerAccessor = new FileContainerAccessor(fileContainerId.value);
  }
  try {
    const uploadedShepardFile = await containerAccessor.uploadFile(file);
    successCount.value += 1;
    return uploadedShepardFile;
  } catch {
    errorCount.value += 1;
  }
}

async function handleFileUpload(): Promise<
  (ShepardFile | undefined)[] | undefined
> {
  if (files.value === undefined) return;
  if (files.value instanceof File) files.value = [files.value];
  if (Array.isArray(files.value)) {
    try {
      return await Promise.all(files.value.map(uploadFile));
    } catch {
      handleError("Could not upload files", "uploading files");
    }

    if (errorCount.value === 0)
      emitSuccess(`${successCount.value} file(s) uploaded successfully.`);
    else {
      const numberOfFiles = (files.value as Array<File>).length;
      handleError(
        `Error: only ${successCount.value} of ${numberOfFiles} files uploaded successfully.`,
        "uploading files",
      );
    }
  }
}

async function handleUserSubmit() {
  uploading.value = true;

  try {
    await createNewFileContainer();
    const uploadedFiles = await handleFileUpload();
    if (uploadedFiles) {
      await createFileReferences(uploadedFiles);
    }
    await updateDefaultFileContainer();
  } catch {
    handleError("Could not upload files", "file upload");
  }

  emits("uploadFinished");
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
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="900">
    <v-card color="canvas" :loading="uploading">
      <template #title>
        <div class="mb-8 text-h4 text-wrap">Upload Files</div>
      </template>
      <template #text>
        <div class="d-flex flex-column ga-4">
          <v-file-upload
            v-model:model-value="files"
            :accept="true"
            :multiple="true"
            class="mb-6"
            clearable
            density="compact"
            icon="mdi-folder-upload-outline"
            show-size
            title="Drag and drop files here (or click to browse)"
          />

          <div class="d-flex flex-column ga-1">
            <div class="d-flex justify-space-between align-center">
              <span class="text-textbody text-subtitle-2">
                Storage Location
              </span>
              <v-switch
                v-model:model-value="isCreatingNewFileContainer"
                density="compact"
                flat
                hide-details
                color="primary"
                label="Create new file container"
              />
            </div>
            <ContainerInput
              v-if="!isCreatingNewFileContainer"
              v-model:container-id="fileContainerId"
              :collection-id="collectionId"
              :is-required="true"
              :container-type="ContainerType.File"
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

          <div class="d-flex flex-column ga-2">
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

          <div class="mt-6">
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
                @click="showDialog = false"
              >
                Cancel
              </v-btn>
              <v-btn
                color="primary"
                variant="flat"
                class="ml-4"
                :disabled="isUploadButtonDisabled"
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
