<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import EntitySelectionPopover from "@/components/generic/EntitySelectionPopover.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import { useSearchContainers } from "@/components/search/InlineSearchContainers";
import FileService from "@/services/fileService";
import { handleError, logError } from "@/utils/error-handling";
import { isNumeric } from "@/utils/helpers";
import type {
  BasicEntity,
  FileContainer,
  FileReference,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { refDebounced } from "@vueuse/core";
import { computed, reactive, ref } from "vue";

defineProps({
  modalId: {
    type: String,
    default: "FileReferenceModal",
  },
  modalName: {
    type: String,
    default: "FileReferenceModal",
  },
});

interface Option {
  value: string;
  text: string;
}

const possibleOids = ref<Option[]>();
const currentContainer = ref<FileContainer>();
const validContainer = ref<boolean>();
const uploadFinished = ref<boolean>();
const uploadActive = ref<boolean>();
const uploadError = ref<boolean>();

const initialState = (): {
  selected: string[];
  currentContainerId: string;
  newContainerName: string;
  newFileReferenceName: string;
  containerSelection: "createNewContainer" | "useExistingContainer";
  fileSelection: "chooseUploadedFile" | "uploadNewFile";
  newFile?: Blob;
} => ({
  selected: [],
  currentContainerId: "",
  newContainerName: "",
  newFileReferenceName: "",
  containerSelection: "useExistingContainer",
  fileSelection: "chooseUploadedFile",
  newFile: undefined,
});

const formData = reactive(initialState());

const userInputSearchContainer = ref("");
const userInputSearchContainerDebounced = refDebounced(
  userInputSearchContainer,
  700,
);
const { results } = useSearchContainers(
  userInputSearchContainerDebounced,
  "FILE",
);
const emit = defineEmits(["create"]);

const selectionValue = computed(() => {
  if (formData.containerSelection == "useExistingContainer") {
    if (formData.fileSelection == "chooseUploadedFile") {
      return "existingContainerUploadedFile";
    } else {
      return "existingContainerNewFile";
    }
  } else {
    setFileSelection("uploadNewFile");
    return "newContainerNewFile";
  }
});

function reset() {
  Object.assign(formData, initialState());
  possibleOids.value = undefined;
  currentContainer.value = undefined;
  validContainer.value = undefined;
  uploadFinished.value = undefined;
  uploadActive.value = undefined;
  uploadError.value = undefined;
  userInputSearchContainer.value = "";
}

function setFileSelection(
  selectedValue: "chooseUploadedFile" | "uploadNewFile",
) {
  formData.fileSelection = selectedValue;
}

async function handleOk() {
  const newFileReference: FileReference = {
    name: "",
    fileOids: [],
    fileContainerId: 0,
  };
  if (selectionValue.value == "existingContainerUploadedFile") {
    newFileReference.fileContainerId = +formData.currentContainerId;
    newFileReference.fileOids = formData.selected;
  } else if (selectionValue.value == "existingContainerNewFile") {
    const uploadedFile = formData.newFile
      ? await uploadFile(formData.newFile, +formData.currentContainerId)
      : undefined;
    if (uploadedFile?.oid) {
      newFileReference.fileContainerId = +formData.currentContainerId;
      newFileReference.fileOids = [uploadedFile.oid];
    }
  } else if (selectionValue.value == "newContainerNewFile") {
    const createdContainer = await createNewFileContainer(
      formData.newContainerName,
    );
    let uploadedFile = undefined;
    if (formData.newFile && createdContainer?.id) {
      uploadedFile = await uploadFile(formData.newFile, createdContainer.id);
    }
    if (uploadedFile?.oid && createdContainer?.id) {
      newFileReference.fileContainerId = createdContainer.id;
      newFileReference.fileOids = [uploadedFile.oid];
    }
  }
  newFileReference.name = formData.newFileReferenceName;
  emit("create", newFileReference);
}

async function createNewFileContainer(newName: string) {
  let response = undefined;
  try {
    response = await FileService.createFileContainer({
      fileContainer: { name: newName },
    });
  } catch (e: any) {
    handleError(e as ResponseError, "creating file container");
  }
  return response;
}

function chooseContainer(container: BasicEntity) {
  if (!container.id) return;
  userInputSearchContainer.value = String(container.id);
  formData.currentContainerId = String(container.id);
  fetchContainer(container.id);
}

function fetchContainer(id: number) {
  FileService.getFileContainer({
    fileContainerId: id,
  })
    .then(container => {
      currentContainer.value = container;
      validContainer.value = true;
      fetchFiles(id);
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file container");
      currentContainer.value = undefined;
      validContainer.value = false;
      possibleOids.value = [];
    });
}

function fetchFiles(id: number) {
  possibleOids.value = [];
  FileService.getAllFiles({
    fileContainerId: id,
  })
    .then(response => {
      response.forEach(file => {
        if (!file.oid) {
          return;
        }
        const option: Option = {
          value: file.oid,
          text: file.oid + " - " + file.filename,
        };
        if (possibleOids.value) possibleOids.value.push(option);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching all files");
    });
}

async function uploadFile(newFile: Blob, containerId: number) {
  uploadActive.value = true;
  let response = undefined;
  try {
    response = await FileService.createFile({
      fileContainerId: containerId,
      file: newFile,
    });
    uploadFinished.value = false;
  } catch (e: any) {
    handleError(e as ResponseError, "uploading file");
    uploadFinished.value = false;
    uploadError.value = true;
  } finally {
    uploadActive.value = false;
  }

  return response;
}
</script>

<template>
  <div>
    <b-modal
      :id="modalId"
      ref="modal"
      size="lg"
      :title="modalName"
      lazy
      @show="reset()"
      @ok="handleOk()"
    >
      <b-form-group>
        <b-container>
          <b-row class="mb-4">
            <b-col>
              <b-form-input
                v-model="formData.newFileReferenceName"
                placeholder="My new reference name"
                required
              ></b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-2 ml-0">
            <b-form-radio-group v-model="formData.containerSelection">
              <b-form-radio value="useExistingContainer">
                use existing container
              </b-form-radio>
              <b-form-radio value="createNewContainer">
                create new container
              </b-form-radio>
            </b-form-radio-group>
          </b-row>

          <div v-if="formData.containerSelection == 'useExistingContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  id="userFormInput"
                  v-model="userInputSearchContainer"
                  placeholder="File Container ID"
                  required
                  :state="validContainer"
                  @blur="
                    if (isNumeric(userInputSearchContainer))
                      fetchContainer(+userInputSearchContainer);
                  "
                ></b-form-input>
                <small v-if="currentContainer">
                  <em> {{ currentContainer.name }} </em>
                </small>
                <small v-else>Please enter a valid container id</small>

                <EntitySelectionPopover
                  :results="results"
                  title-text="search for file containers by name, username, id or description"
                  @selected="chooseContainer($event)"
                />
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="formData.fileSelection">
                <b-form-radio value="chooseUploadedFile">
                  choose uploaded file
                </b-form-radio>
                <b-form-radio value="uploadNewFile">
                  upload new file
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <div v-if="formData.containerSelection == 'createNewContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  v-model="formData.newContainerName"
                  placeholder="My new container name"
                  required
                ></b-form-input>
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="formData.fileSelection">
                <b-form-radio value="chooseUploadedFile" disabled>
                  choose uploaded file
                </b-form-radio>
                <b-form-radio value="uploadNewFile">
                  upload new file
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <b-row
            v-if="formData.fileSelection == 'chooseUploadedFile'"
            class="mb-4"
          >
            <b-col>
              <b-form-select
                v-model="formData.selected"
                :options="possibleOids"
                multiple
                required
              ></b-form-select>
              <small>Please select your files</small>
            </b-col>
          </b-row>

          <b-row v-if="formData.fileSelection == 'uploadNewFile'" class="mb-4">
            <b-col>
              <b-form-file
                v-model="formData.newFile"
                placeholder="Choose a file or drop it here..."
              >
              </b-form-file>
            </b-col>
          </b-row>
        </b-container>
      </b-form-group>
    </b-modal>

    <ProcessAlert
      process-name="Upload"
      :process-active="uploadActive"
      :process-finished="uploadFinished"
      :process-error="uploadError"
      @success-message-dismissed="uploadFinished = false"
      @error-message-dismissed="uploadError = false"
    />
  </div>
</template>
