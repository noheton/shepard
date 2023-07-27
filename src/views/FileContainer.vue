<script setup lang="ts">
import UploadFileModal from "@/components/containers/UploadFileModal.vue";
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import FileService from "@/services/fileService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import type {
  FileContainer,
  Permissions,
  ResponseError,
  Roles,
  ShepardFile,
} from "@dlr-shepard/shepard-client";
import { useTitle } from "@vueuse/core";
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue2-helpers/vue-router";
import CurrentRoleIcon from "../components/generic/CurrentRoleIcon.vue";

const route = useRoute();
const router = useRouter();

const currentFileContainer = ref<FileContainer>();
const permissions = ref<Permissions>();
const currentFile = ref<ShepardFile>();
const fileList = ref<Array<ShepardFile>>([]);

const downloadActive = ref<boolean>(false);
const downloadFinished = ref<boolean>(false);
const downloadError = ref<boolean>(false);
const uploadActive = ref<boolean>(false);
const uploadFinished = ref<boolean>(false);
const uploadError = ref<boolean>(false);
const deletedAlert = ref<boolean>(false);

const currentFileContainerId = computed<string>(() => route.params.fileId);

function retrieveFileContainer() {
  FileService.getFileContainer({
    fileContainerId: +currentFileContainerId.value,
  })
    .then(response => {
      currentFileContainer.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file container");
    });
}

function retrieveFileList() {
  FileService.getAllFiles({
    fileContainerId: +currentFileContainerId.value,
  })
    .then(response => {
      fileList.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
    });
}

function handleDeleteContainer() {
  FileService.deleteFileContainer({
    fileContainerId: +currentFileContainerId.value,
  })
    .then(() => {
      router.push({ name: "FilesList" });
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting file container");
    });
}

function uploadFile(newFile: Blob) {
  uploadActive.value = true;
  if (currentFileContainer.value?.id)
    FileService.createFile({
      fileContainerId: +currentFileContainer.value.id,
      file: newFile,
    })
      .then(() => {
        retrieveFileList();
        uploadFinished.value = true;
      })
      .catch(e => {
        handleError(e as ResponseError, "uploading file");
        uploadError.value = true;
      })
      .finally(() => {
        uploadActive.value = false;
      });
}

function handleDownloadFile(oid: string, filename?: string) {
  downloadActive.value = true;
  if (currentFileContainer.value?.id)
    FileService.getFile({
      fileContainerId: currentFileContainer.value?.id,
      oid: oid,
    })
      .then(response => {
        downloadFile(response, filename);
        downloadFinished.value = true;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching file");
        downloadError.value = true;
      })
      .finally(() => {
        downloadActive.value = false;
      });
}

function handleDeleteFile() {
  if (!currentFile.value?.oid || !currentFileContainer.value?.id) return;
  FileService.deleteFile({
    fileContainerId: currentFileContainer.value?.id,
    oid: currentFile.value.oid,
  })
    .then(() => {
      deletedAlert.value = true;
      retrieveFileList();
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting file");
    });
}

function retrievePermissions() {
  FileService.getFilePermissions({
    fileContainerId: +currentFileContainerId.value,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching permissions");
    });
}

function updatePermissions(perms: Permissions) {
  FileService.editFilePermissions({
    fileContainerId: +currentFileContainerId.value,
    permissions: perms,
  })
    .then(response => {
      permissions.value = response;
    })
    .catch(e => {
      handleError(e as ResponseError, "updating permissions");
    });
}

const roles = ref<Roles | undefined>();
function retrieveRoles() {
  FileService.getFileRoles({
    fileContainerId: +currentFileContainerId.value,
  })
    .then(response => {
      roles.value = response;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching roles");
    });
}

const title = computed(() => {
  return currentFileContainer.value?.name || "File Container";
});
function updateTitle() {
  useTitle(title, {
    titleTemplate: "%s | shepard",
  });
}

onMounted(() => {
  retrieveFileContainer();
  retrieveFileList();
  retrieveRoles();
  updateTitle();
});
</script>

<template>
  <div v-if="currentFileContainer" class="view">
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="info"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <b-button-group v-if="roles?.owner || roles?.writer" class="float-right">
      <b-button
        v-b-modal.upload-file-to-container-modal
        v-b-tooltip.hover
        title="Upload File"
        variant="primary"
      >
        <CreateIcon />
      </b-button>
      <b-button
        v-if="roles?.owner || roles?.manager"
        v-b-modal.permissions-modal
        v-b-tooltip.hover
        title="Edit Permissions"
        variant="secondary"
        @click="retrievePermissions()"
      >
        <PermissionsIcon />
      </b-button>
      <b-button
        v-b-modal.delete-container-confirmation-modal
        v-b-tooltip.hover
        title="Delete"
        variant="info"
      >
        <DeleteIcon />
      </b-button>
    </b-button-group>
    <h3>
      {{ currentFileContainer.name }}
      <CurrentRoleIcon :roles="roles" />
    </h3>
    <div class="mb-3">
      <b>ID:</b> {{ currentFileContainer.id }}<br />
      <b>Oid:</b> {{ currentFileContainer.oid }}<br />
      <CreatedByLine
        :created-at="currentFileContainer.createdAt"
        :created-by="currentFileContainer.createdBy"
        tooltip
      />
    </div>
    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-finished="downloadFinished"
      :process-error="downloadError"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />
    <ProcessAlert
      process-name="Upload"
      :process-active="uploadActive"
      :process-finished="uploadFinished"
      :process-error="uploadError"
      @success-message-dismissed="uploadFinished = false"
      @error-message-dismissed="uploadError = false"
    />

    <b-list-group>
      <b-list-group-item v-for="(file, index) in fileList" :key="index">
        <div v-if="file.createdAt" class="float-left">
          <b><GenericName :name="file.filename || ''" :word-count="40" /></b>
          | {{ file.oid }} |
          {{ new Date(file.createdAt).toLocaleString() }}
          <br />
          <em v-if="file.md5"> md5: {{ file.md5 }} </em>
        </div>
        <b-button-group class="float-right">
          <b-button
            v-b-modal.file-download-confirmation-modal
            v-b-tooltip.hover
            title="Download"
            variant="secondary"
            @click="
              if (file.oid != undefined)
                handleDownloadFile(file.oid, file.filename);
            "
          >
            <DownloadIcon />
          </b-button>
          <b-button
            v-b-modal.delete-file-confirmation-modal
            v-b-tooltip.hover
            title="Delete"
            variant="info"
            @click="currentFile = file"
          >
            <DeleteIcon />
          </b-button>
        </b-button-group>
      </b-list-group-item>
    </b-list-group>

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
      @confirmation="handleDeleteFile()"
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
      :entity-id="+currentFileContainerId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>
