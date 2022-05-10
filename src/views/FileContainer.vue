<template>
  <div v-if="currentFileContainer" class="file-container">
    <div class="component">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.upload-file-to-container-modal
          v-b-tooltip.hover
          title="Upload File"
          variant="primary"
        >
          <CreateIcon />
        </b-button>
        <b-button
          v-if="managerAccess"
          v-b-modal.permissions-modal
          v-b-tooltip.hover
          title="Edit Permissions"
          variant="light"
        >
          <PermissionsIcon />
        </b-button>
        <b-button
          v-b-modal.delete-container-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>{{ currentFileContainer.name }}</h3>
      <p>
        <b>ID:</b> {{ currentFileContainer.id }}<br />
        <b>Oid:</b> {{ currentFileContainer.oid }}<br />
        <CreatedByLine
          :created-at="currentFileContainer.createdAt"
          :created-by="currentFileContainer.createdBy"
          tooltip
        />
      </p>
      <DownloadAlert
        :download-active="downloadActive"
        :download-started="downloadStarted"
        :download-error="downloadError"
      />

      <b-list-group>
        <b-list-group-item v-for="(file, index) in fileList" :key="index">
          <div class="float-left">
            <b><GenericName :name="file.filename" :word-count="40" /></b> |
            {{ file.oid }} | {{ new Date(file.createdAt).toLocaleString() }}
          </div>
          <b-button-group class="float-right">
            <b-button
              v-b-modal.file-download-confirmation-modal
              v-b-tooltip.hover
              title="Download"
              variant="success"
              @click="downloadFile(file.oid, file.filename)"
            >
              <DownloadIcon />
            </b-button>
            <b-button
              v-b-modal.delete-file-confirmation-modal
              v-b-tooltip.hover
              title="Delete"
              variant="dark"
              @click="currentFile = file"
            >
              <DeleteIcon />
            </b-button>
          </b-button-group>
        </b-list-group-item>
      </b-list-group>
    </div>

    <UploadFileModal
      modal-id="upload-file-to-container-modal"
      modal-name="Upload File To Container"
      @created="uploadFile($event)"
    />

    <DeleteConfirmationModal
      v-if="currentFile"
      modal-id="delete-file-confirmation-modal"
      modal-name="Confirm to delete file"
      :modal-text="
        'Do you really want do delete the file with name ' +
        currentFile.filename +
        '?'
      "
      @confirmation="handleDeleteFile(currentFile.oid)"
    />

    <DeleteConfirmationModal
      modal-id="delete-container-confirmation-modal"
      modal-name="Confirm to delete file container"
      :modal-text="
        'Do you really want do delete the file container with name ' +
        currentFileContainer.name +
        '?'
      "
      @confirmation="handleDeleteContainer()"
    />

    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentFileContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>

<script lang="ts">
import UploadFileModal from "@/components/containers/UploadFileModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import DownloadAlert from "@/components/DownloadAlert.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import FileService from "@/services/fileService";
import { downloadFile } from "@/utils/download";
import { emitter } from "@/utils/event-bus";
import {
  FileContainer,
  Permissions,
  ShepardFile,
} from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface FileData {
  currentFileContainer?: FileContainer;
  permissions?: Permissions;
  downloadStarted: boolean;
  downloadActive: boolean;
  downloadError: boolean;
  fileList: ShepardFile[];
  currentFile?: ShepardFile;
  managerAccess: boolean;
}

export default Vue.extend({
  components: {
    CreatedByLine,
    DeleteConfirmationModal,
    PermissionsModal,
    UploadFileModal,
    DownloadAlert,
    GenericName,
  },
  data() {
    return {
      currentFileContainer: undefined,
      permissions: undefined,
      downloadStarted: false,
      downloadActive: false,
      downloadError: false,
      fileList: [],
      currentFile: undefined,
      managerAccess: false,
    } as FileData;
  },
  computed: {
    currentFileContainerId(): number {
      return Number(this.$router.currentRoute.params.fileId);
    },
  },
  mounted() {
    this.retrieveFileContainer();
    this.retrieveFileList();
    this.retrievePermissions();
  },
  methods: {
    retrieveFileContainer() {
      FileService.getFileContainer({
        fileContainerId: this.currentFileContainerId,
      })
        .then(response => {
          this.currentFileContainer = response;
        })
        .catch(e => {
          const error = "Error while fetching file container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    retrieveFileList() {
      FileService.getAllFiles({
        fileContainerId: this.currentFileContainerId,
      })
        .then(response => {
          this.fileList = response;
        })
        .catch(e => {
          const error = "Error while fetching file payload: " + e.statusText;
          console.log(error);
        });
    },
    handleDeleteContainer() {
      FileService.deleteFileContainer({
        fileContainerId: this.currentFileContainerId,
      })
        .then(() => {
          this.$router.push({ name: "FilesList" });
        })
        .catch(e => {
          const error = "Error while deleting file container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    uploadFile(newFile: Blob) {
      if (this.currentFileContainer?.id)
        FileService.createFile({
          fileContainerId: this.currentFileContainer?.id,
          file: newFile,
        })
          .then(() => {
            this.retrieveFileList();
          })
          .catch(e => {
            const error = "Error while uploading File: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
          })
          .finally();
    },
    downloadFile(oid: string, filename: string) {
      this.downloadStarted = true;
      this.downloadActive = true;
      if (this.currentFileContainer?.id)
        FileService.getFile({
          fileContainerId: this.currentFileContainer?.id,
          oid: oid,
        })
          .then(response => {
            downloadFile(response, filename);
          })
          .catch(e => {
            const error = "Error while fetching file: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
            this.downloadStarted = false;
            this.downloadError = true;
          })
          .finally(() => (this.downloadActive = false));
    },

    handleDeleteFile(oid: string) {
      if (this.currentFileContainer?.id)
        FileService.deleteFile({
          fileContainerId: this.currentFileContainer?.id,
          oid: oid,
        })
          .catch(e => {
            const error = "Error while deleting file: " + e.statusText;
            console.log(error);
            emitter.emit("error", error);
          })
          .finally(() => {
            this.retrieveFileList();
          });
    },
    retrievePermissions() {
      FileService.getFilePermissions({
        fileContainerId: this.currentFileContainerId,
      })
        .then(response => {
          this.permissions = response;
          this.managerAccess = true;
        })
        .catch(e => {
          const error = "Error while fetching permissons: " + e.statusText;
          console.log(error);
          this.managerAccess = e.status != 403;
        });
    },
    updatePermissions(perms: Permissions) {
      FileService.editFilePermissions({
        fileContainerId: this.currentFileContainerId,
        permissions: perms,
      })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while updating permissons: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
