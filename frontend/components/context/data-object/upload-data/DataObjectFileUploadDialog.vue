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
import { useFetchCollectionContainers } from "~/composables/context/useFetchCollectionContainers";
import { useCreateFileContainer } from "~/composables/data/useCreateFileContainer";
import { useCreateFileReference } from "~/composables/references/useCreateFileReference";
import { useCreateSingletonFileReference } from "~/composables/references/useCreateSingletonFileReference";
import type { FileRef } from "~/components/context/data-references/create-dialog/DataRef";

interface FileUploadDialogProps {
  collectionId: number;
  dataobjectId: number;
  /**
   * When non-null, the dialog can call POST /v2/files in singleton mode.
   * When undefined the dialog auto-fetches the appId from the v1 endpoint
   * the first time singleton mode is needed.
   * SINGLETON-FILE-04.
   */
  dataObjectAppId?: string;
  createReference: boolean;
  accept?: string;
}

const props = withDefaults(defineProps<FileUploadDialogProps>(), {
  accept: "*",
  dataObjectAppId: undefined,
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

// SINGLETON-FILE-04 — POST /v2/files wrapper. Used when uploadMode === "singleton".
const { createSingleton } = useCreateSingletonFileReference();

// SINGLETON-FILE-04 — default mode is FR1b singleton-per-file when
// `createReference === true`. When the parent only wants bytes uploaded
// (lab-journal markdown embed: `:create-reference="false"`), the
// singleton path is wrong — POST /v2/files *always* mints a
// :SingletonFileReference. Force bundle mode in that case so the dialog
// falls back to the bytes-only FileContainer upload path.
//
//   "singleton" → one POST /v2/files per file (one :SingletonFileReference each).
//   "bundle"    → legacy FR1a path (one :FileBundleReference holding N files,
//                 OR — when createReference is false — bytes only, no reference).
const uploadMode = ref<"singleton" | "bundle">(
  props.createReference ? "singleton" : "bundle",
);

// Resolve the parent DataObject's appId for the singleton POST path. The v1
// endpoint includes appId on the wire even on the upstream-byte-compat
// surface (one of the few fields where appId leaks through verbatim).
const dataObjectAppId = ref<string | undefined>(props.dataObjectAppId);
async function ensureDataObjectAppId(): Promise<string | undefined> {
  if (dataObjectAppId.value) return dataObjectAppId.value;
  try {
    const { data: session } = useAuth();
    const token = session.value?.accessToken;
    const base = (useRuntimeConfig().public.backendApiUrl as string).replace(/\/$/, "");
    const resp = await fetch(
      `${base}/collections/${props.collectionId}/dataObjects/${props.dataobjectId}`,
      { headers: { Authorization: `Bearer ${token}`, Accept: "application/json" } },
    );
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const body = await resp.json();
    dataObjectAppId.value = body.appId;
    return body.appId;
  } catch (e) {
    handleError(e, "resolving DataObject appId for singleton upload");
    return undefined;
  }
}

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

// CC1d: "link" = link to an existing collection container; "create" = create a new one.
// Default to "link" so the most common case (container already exists) requires zero
// extra clicks.
const containerMode = ref<"link" | "create">("link");

// Backwards-compat alias used by createNewFileContainer() and the CC1c name-prefill watch.
const isCreatingNewFileContainer = computed(() => containerMode.value === "create");

// CC1d: collection-scoped FILE containers for the "Link existing" list.
const collectionAppId = computed(
  () => collectionAccessor.collection.value?.appId ?? null,
);
const { containers: collectionContainers, isLoading: containersLoading } =
  useFetchCollectionContainers(collectionAppId);
const fileContainersInCollection = computed(() =>
  collectionContainers.value.filter(c => c.containerType === "FILE"),
);

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
  if (files.value === undefined) return true;
  if (Array.isArray(files.value) && files.value.length === 0) return true;
  // Singleton mode bypasses all FileContainer + per-batch reference-name
  // requirements — each file becomes its own :SingletonFileReference whose
  // name is derived from the filename. The only gate is that there's at
  // least one file selected (checked above).
  if (uploadMode.value === "singleton") return false;
  // Bundle mode — preserve existing gating.
  return (
    (props.createReference && !newReferenceName.value) ||
    (containerMode.value === "link" && fileContainerId.value === undefined) ||
    (containerMode.value === "create" && !newFileContainerName.value)
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
  const oids = files
    .filter(entry => entry !== undefined)
    .filter(file => file.oid !== undefined)
    .map(file => file.oid!);
  // Surface the upload failure honestly instead of POSTing a body the
  // backend will reject with the truncated "Error while createFileReference:"
  // toast. If files were picked but no oids came back, the upload step
  // itself failed.
  if (oids.length === 0) {
    handleError(
      new Error(
        files.length === 0
          ? "No files selected for the reference."
          : "File upload didn't return any file IDs — check that the file container is reachable and try again.",
      ),
      "createFileReference",
    );
    return;
  }
  const fileRef: FileRef = { fileOids: oids };
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
  if (containerMode.value === "create") {
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

/**
 * SINGLETON-FILE-04 — singleton upload path.
 *
 * For each selected File, POST /v2/files?parentDataObjectAppId=…&name=…
 * with a multipart `file=…` body. Each call mints one
 * :SingletonFileReference whose appId resolves directly to bytes via
 * GET /v2/files/{appId}/content. The Reference name is derived from
 * the filename (with the standard yyyy-MM-dd date prefix matching the
 * existing bundle-mode `updateReferenceNameByFileName` heuristic).
 *
 * No FileContainer is involved — singletons live in the shared
 * `_shepard_files` Mongo namespace.
 */
async function handleSingletonSubmit() {
  const appId = await ensureDataObjectAppId();
  if (!appId) {
    handleError("Could not resolve DataObject appId", "singleton upload");
    return;
  }
  const filesArr = Array.isArray(files.value) ? files.value : [];
  const signal = progress.startBatch(filesArr);
  const datePrefix = currentDateShortForm();
  try {
    for (let i = 0; i < filesArr.length; i += 1) {
      if (signal.aborted) {
        progress.markCancelled(i);
        continue;
      }
      const f = filesArr[i]!;
      progress.markStarted(i);
      try {
        const refName = `${datePrefix}-${f.name}`;
        const created = await createSingleton({
          parentDataObjectAppId: appId,
          name: refName,
          file: f,
        });
        if (!created) {
          progress.markError(i, "Upload returned no appId");
        } else {
          progress.markDone(i);
          successCount.value += 1;
        }
      } catch (e) {
        progress.markError(i, (e as Error).message ?? "Singleton upload failed");
      }
    }
  } finally {
    progress.finishBatch();
  }
  if (successCount.value > 0) {
    emitSuccess(
      `${successCount.value} singleton FileReference(s) created from ${filesArr.length} file(s).`,
    );
  }
  // Notify parent (`fileContainerId` is irrelevant for singletons — emit -1
  // to signal "no container was touched"; consumers that don't use
  // the second arg can ignore).
  emit("files-uploaded", [], -1);
}

async function handleUserSubmit() {
  uploading.value = true;

  try {
    if (uploadMode.value === "singleton") {
      await handleSingletonSubmit();
    } else {
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
    }
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
// the user switches to the "Create new" tab. Only fills when the field is still
// blank so the user can type their own name without it being overwritten.
watch(containerMode, (mode: "link" | "create") => {
  if (mode === "create" && !newFileContainerName.value) {
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
          <!-- CC1c: first-time explanation of the Collection / Container duality
               — but only true in bundle mode (singletons live in the shared
               `_shepard_files` namespace, not in a per-bundle FileContainer). -->
          <v-alert
            v-if="uploadMode === 'bundle'"
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

          <!-- SINGLETON-FILE-04: upload-mode toggle. Default is "singleton".
               Operator opts in to "bundle" only when files genuinely belong
               together (image series, mesh sets, archive contents). Hidden
               entirely when createReference === false (lab-journal markdown
               embeds, which want bytes only, never a Reference). -->
          <div v-if="!uploading && createReference" class="d-flex flex-column ga-1">
            <div class="d-flex justify-space-between align-center">
              <span class="text-textbody1 text-subtitle-2">
                Upload Shape
              </span>
              <v-btn-toggle
                v-model="uploadMode"
                color="primary"
                density="compact"
                mandatory
                rounded="lg"
                variant="outlined"
                data-testid="upload-mode-toggle"
              >
                <v-btn value="singleton" size="small" data-testid="upload-mode-singleton">
                  <v-icon start size="small">mdi-file-outline</v-icon>
                  One Reference per file
                </v-btn>
                <v-btn value="bundle" size="small" data-testid="upload-mode-bundle">
                  <v-icon start size="small">mdi-folder-zip-outline</v-icon>
                  Bundle as one Reference
                </v-btn>
              </v-btn-toggle>
            </div>
            <v-alert
              v-if="uploadMode === 'singleton'"
              density="compact"
              type="info"
              variant="tonal"
              class="mt-1"
              data-testid="upload-mode-singleton-help"
            >
              Each file becomes its own FileReference. Use this when the
              files are independent (a CAD model, a PDF, a robot URDF,
              a KRL program).
            </v-alert>
            <v-alert
              v-else
              density="compact"
              type="warning"
              variant="tonal"
              class="mt-1"
              data-testid="upload-mode-bundle-help"
            >
              All selected files share one FileReference (FileBundle). Use this
              only when the files genuinely belong together — an image series,
              a mesh set, the unpacked contents of an archive.
            </v-alert>
          </div>

          <div v-if="!uploading && uploadMode === 'bundle'" class="d-flex flex-column ga-1">
            <div class="d-flex justify-space-between align-center">
              <span class="text-textbody1 text-subtitle-2">
                Storage Location
              </span>
              <!-- CC1d: tab toggle for "Link existing" vs "Create new" -->
              <v-btn-toggle
                v-model="containerMode"
                color="primary"
                density="compact"
                mandatory
                rounded="lg"
                variant="outlined"
              >
                <v-btn value="link" size="small">
                  <v-icon start size="small">mdi-link-variant</v-icon>
                  Link existing
                </v-btn>
                <v-btn value="create" size="small">
                  <v-icon start size="small">mdi-plus</v-icon>
                  Create new
                </v-btn>
              </v-btn-toggle>
            </div>

            <!-- CC1d: "Link existing" panel — collection-scoped FILE containers -->
            <template v-if="containerMode === 'link'">
              <v-autocomplete
                v-if="fileContainersInCollection.length > 0"
                v-model="fileContainerId"
                :items="fileContainersInCollection"
                :loading="containersLoading"
                item-title="name"
                item-value="id"
                label="Select file container *"
                density="compact"
                variant="outlined"
                clearable
                no-data-text="No file containers in this collection yet"
              />
              <ContainerInput
                v-else
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
              <v-alert
                v-if="fileContainersInCollection.length === 0 && !containersLoading"
                density="compact"
                type="info"
                variant="tonal"
                class="mt-1"
              >
                No file containers are linked to this collection yet — search
                globally above or switch to "Create new" to add one.
              </v-alert>
            </template>

            <!-- "Create new" panel — unchanged from CC1c -->
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
                  fileContainerId || containerMode === 'create'
              "
              v-model="isFileContainerDefault"
              color="primary"
              density="compact"
              label="Set as new default file container"
            />
          </div>

          <div
            v-if="createReference && !uploading && uploadMode === 'bundle'"
            class="d-flex flex-column ga-2"
          >
            <div class="d-flex ga-2 align-center">
              <span class="text-textbody1 text-subtitle-2">Reference Name</span>
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
