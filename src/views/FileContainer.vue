<template>
  <div v-if="currentFile" class="file-container">
    <div class="component">
      <b-button-group class="float-right">
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
          v-b-modal.delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="dark"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>
      <h3>{{ currentFile.name }}</h3>
      <p>
        <b>ID:</b> {{ currentFile.id }}<br />
        <b>Oid:</b> {{ currentFile.oid }}<br />
        <CreatedByLine
          :created-at="currentFile.createdAt"
          :created-by="currentFile.createdBy"
          tooltip
        />
      </p>
      <b-list-group>
        <b-list-group-item v-for="(file, index) in fileList" :key="index">
          {{ file.oid }} | {{ file.filename }}
        </b-list-group-item>
      </b-list-group>
    </div>
    <DeleteConfirmationModal
      modal-id="delete-confirmation-modal"
      modal-name="Confirm to delete file container"
      :modal-text="
        'Do you really want do delete the file container with name ' +
        currentFile.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
    <PermissionsModal
      modal-id="permissions-modal"
      modal-name="Edit Permissions"
      :entity-id="currentFileId"
      :old-permissions="permissions"
      @update="updatePermissions($event)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import PermissionsModal from "@/components/PermissionsModal.vue";
import { FileVue } from "@/utils/api-mixin";
import { emitter } from "@/utils/event-bus";
import { FileContainer, Permissions } from "@dlr-shepard/shepard-client";
import Vue, { VueConstructor } from "vue";

interface FileData {
  currentFile?: FileContainer;
  permissions?: Permissions;
  fileList: unknown[];
  managerAccess: boolean;
}

export default (
  Vue as VueConstructor<Vue & InstanceType<typeof FileVue>>
).extend({
  components: { CreatedByLine, DeleteConfirmationModal, PermissionsModal },
  mixins: [FileVue],
  data() {
    return {
      currentFile: undefined,
      permissions: undefined,
      fileList: [],
      managerAccess: false,
    } as FileData;
  },
  computed: {
    currentFileId(): number {
      return Number(this.$router.currentRoute.params.fileId);
    },
  },
  mounted() {
    this.retrieveFile();
    this.retrieveFileList();
    this.retrievePermissions();
  },
  methods: {
    retrieveFile() {
      this.fileApi
        ?.getFileContainer({
          fileContainerId: this.currentFileId,
        })
        .then(response => {
          this.currentFile = response;
        })
        .catch(e => {
          const error = "Error while fetching file container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    retrieveFileList() {
      this.fileApi
        ?.getAllFiles({
          fileContainerId: this.currentFileId,
        })
        .then(response => {
          this.fileList = response;
        })
        .catch(e => {
          const error = "Error while fetching file payload: " + e.statusText;
          console.log(error);
        });
    },
    handleDelete() {
      this.fileApi
        ?.deleteFileContainer({ fileContainerId: this.currentFileId })
        .then(() => {
          this.$router.push({ name: "FilesList" });
        })
        .catch(e => {
          const error = "Error while deleting file container: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    retrievePermissions() {
      this.fileApi
        ?.getFilePermissions({ fileContainerId: this.currentFileId })
        .then(response => {
          this.permissions = response;
          this.managerAccess = true;
        })
        .catch(e => {
          const error = "Error while fetching permissons: " + e.statusText;
          console.log(error);
          this.managerAccess = false;
        });
    },
    updatePermissions(perms: Permissions) {
      this.fileApi
        ?.editFilePermissions({
          fileContainerId: this.currentFileId,
          permissions: perms,
        })
        .then(response => {
          this.permissions = response;
        })
        .catch(e => {
          const error = "Error while editing permissons: " + e.statusText;
          console.log(error);
        });
    },
  },
});
</script>
