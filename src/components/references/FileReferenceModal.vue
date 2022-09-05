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
                v-model="newFileReference.name"
                placeholder="My new reference name"
                required
              ></b-form-input>
            </b-col>
          </b-row>

          <b-row class="mb-2 ml-0">
            <b-form-radio-group v-model="containerSelection">
              <b-form-radio value="useExistingContainer">
                use existing container
              </b-form-radio>
              <b-form-radio value="createNewContainer">
                create new container
              </b-form-radio>
            </b-form-radio-group>
          </b-row>

          <div v-if="containerSelection == 'useExistingContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  v-model="currentContainerId"
                  placeholder="File container id"
                  type="number"
                  required
                  :state="validContainer"
                  @blur="fetchContainer()"
                ></b-form-input>
                <small v-if="currentContainer">
                  <em> {{ currentContainer.name }} </em>
                </small>
                <small v-else>Please enter a valid container id</small>
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="fileSelection">
                <b-form-radio value="chooseUploadedFile">
                  choose uploaded file
                </b-form-radio>
                <b-form-radio value="uploadNewFile">
                  upload new file
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <div v-if="containerSelection == 'createNewContainer'">
            <b-row class="mb-4">
              <b-col>
                <b-form-input
                  v-model="newContainerName"
                  placeholder="My new container name"
                  required
                ></b-form-input>
              </b-col>
            </b-row>

            <b-row class="mb-2 ml-0">
              <b-form-radio-group v-model="fileSelection">
                <b-form-radio value="chooseUploadedFile" disabled>
                  choose uploaded file
                </b-form-radio>
                <b-form-radio value="uploadNewFile">
                  upload new file
                </b-form-radio>
              </b-form-radio-group>
            </b-row>
          </div>

          <b-row v-if="fileSelection == 'chooseUploadedFile'" class="mb-4">
            <b-col>
              <b-form-select
                v-model="selected"
                :options="possibleOids"
                multiple
                required
              ></b-form-select>
              <small>Please select your files</small>
            </b-col>
          </b-row>

          <b-row v-if="fileSelection == 'uploadNewFile'" class="mb-4">
            <b-col>
              <b-form-file
                v-model="newFile"
                variant="primary"
                placeholder="Upload File"
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

<script lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */

import ProcessAlert from "@/components/ProcessAlert.vue";
import FileService from "@/services/fileService";
import { handleError, logError } from "@/utils/error-handling";
import type {
  FileContainer,
  FileReference,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface Option {
  value: string;
  text: string;
}

interface FileReferenceModelData {
  newFileReference: FileReference;
  possibleOids: Array<Option>;
  selected: Array<string>;
  currentContainerId: string;
  currentContainer?: FileContainer;
  validContainer?: boolean;
  newContainerName: string;
  containerSelection: "createNewContainer" | "useExistingContainer";
  fileSelection: "chooseUploadedFile" | "uploadNewFile";
  newFile?: Blob;
  uploadFinished: boolean;
  uploadActive: boolean;
  uploadError: boolean;
}

function initialState(): FileReferenceModelData {
  return {
    newFileReference: {
      name: "",
      fileOids: [],
      fileContainerId: 0,
    },
    possibleOids: [],
    selected: [],
    currentContainerId: "",
    currentContainer: undefined,
    validContainer: undefined,
    newContainerName: "",
    containerSelection: "useExistingContainer",
    fileSelection: "chooseUploadedFile",
    newFile: undefined,
    uploadFinished: false,
    uploadActive: false,
    uploadError: false,
  };
}

export default defineComponent({
  components: {
    ProcessAlert,
  },
  props: {
    modalId: {
      type: String,
      default: "FileReferenceModal",
    },
    modalName: {
      type: String,
      default: "FileReferenceModal",
    },
  },
  emits: ["create"],
  data() {
    return initialState();
  },

  computed: {
    selectionValue():
      | "existingContainerUploadedFile"
      | "existingContainerNewFile"
      | "newContainerNewFile" {
      if (this.containerSelection == "useExistingContainer") {
        if (this.fileSelection == "chooseUploadedFile") {
          return "existingContainerUploadedFile";
        } else {
          return "existingContainerNewFile";
        }
      } else {
        this.setFileSelection("uploadNewFile");
        return "newContainerNewFile";
      }
    },
  },

  methods: {
    reset() {
      Object.assign(this.$data, initialState());
    },

    setFileSelection(selectedValue: "chooseUploadedFile" | "uploadNewFile") {
      this.fileSelection = selectedValue;
    },

    async handleOk() {
      if (this.selectionValue == "existingContainerUploadedFile") {
        this.newFileReference.fileOids = this.selected;
      } else if (this.selectionValue == "existingContainerNewFile") {
        if (this.newFile) {
          const uploadedFile = await this.uploadFile(
            this.newFile,
            +this.currentContainerId,
          );

          if (uploadedFile && uploadedFile.oid) {
            this.newFileReference.fileOids = [uploadedFile.oid];
          }
        }
      } else if (this.selectionValue == "newContainerNewFile") {
        const createdContainer = await this.createNewFileContainer(
          this.newContainerName,
        );
        if (this.newFile && createdContainer && createdContainer.id) {
          this.newFileReference.fileContainerId = createdContainer.id;

          const uploadedFile = await this.uploadFile(
            this.newFile,
            createdContainer.id,
          );
          if (uploadedFile && uploadedFile.oid)
            this.newFileReference.fileOids = [uploadedFile.oid];
        }
      }
      this.$emit("create", this.newFileReference);
    },

    async createNewFileContainer(newName: string) {
      let response = undefined;
      try {
        response = await FileService.createFileContainer({
          fileContainer: { name: newName } as FileContainer,
        });
      } catch (e: any) {
        handleError(e as ResponseError, "creating file container");
      }
      return response;
    },

    fetchContainer() {
      FileService.getFileContainer({
        fileContainerId: +this.currentContainerId,
      })
        .then(container => {
          this.currentContainer = container;
          this.validContainer = true;
          this.fetchFiles();
          if (container.id)
            this.newFileReference.fileContainerId = container.id;
        })
        .catch(e => {
          logError(e as ResponseError, "fetching file container");
          this.currentContainer = undefined;
          this.validContainer = false;
        });
    },

    fetchFiles() {
      FileService.getAllFiles({
        fileContainerId: +this.currentContainerId,
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
            this.possibleOids.push(option);
          });
        })
        .catch(e => {
          handleError(e as ResponseError, "fetching all files");
        });
    },
    async uploadFile(newFile: Blob, containerId: number) {
      this.uploadActive = true;
      let response = undefined;
      try {
        response = await FileService.createFile({
          fileContainerId: containerId,
          file: newFile,
        });
        this.uploadFinished = false;
      } catch (e: any) {
        handleError(e as ResponseError, "uploading file");
        this.uploadFinished = false;
        this.uploadError = true;
      } finally {
        this.uploadActive = false;
      }

      return response;
    },
  },
});
</script>
