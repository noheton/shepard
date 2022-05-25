<template>
  <div class="list">
    <b-alert
      :show="createdAlert"
      dismissible
      variant="success"
      @dismissed="createdAlert = false"
    >
      Successfully created
    </b-alert>
    <b-alert
      :show="deletedAlert"
      dismissible
      variant="danger"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-started="downloadStarted"
      :process-error="downloadError"
    />

    <b-button v-b-modal.create-file-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <FileReferenceModal
      modal-id="create-file-ref-modal"
      modal-name="Create File Reference"
      @create="create($event)"
    />

    <b-list-group>
      <b-list-group-item
        v-for="(fileReference, index) in fileReferenceList"
        :key="index"
      >
        <div>
          <b><GenericName :name="fileReference.name" /></b> | ID:
          {{ fileReference.id }} |
          <span v-if="fileReference.fileContainerId != -1">
            <b-link
              :to="{
                name: 'Files',
                params: { fileId: fileReference.fileContainerId },
              }"
            >
              Container: {{ fileReference.fileContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>

          <b-button
            v-b-modal.file-reference-delete-confirmation-modal
            v-b-tooltip.hover
            class="float-right"
            title="Delete"
            variant="dark"
            @click="currentFileReference = fileReference"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="fileReference.createdBy"
          :created-at="fileReference.createdAt"
        />
        <div v-for="(oid, i) in fileReference.fileOids" :key="i">
          <small v-if="files[oid]">
            <a v-if="fileReference.fileContainerId != -1">
              <b-link
                :disabled="downloadActive"
                @click="
                  getFilePayload(fileReference.id, oid, files[oid].filename)
                "
              >
                <DownloadIcon :size="18" />
              </b-link>
            </a>
            <a v-else><DownloadIcon :size="18" class="text-danger" /></a>
            <b> Oid:</b> <tt>{{ oid }}</tt>
            <span v-if="files[oid].createdAt">
              | <b>Created at:</b>
              {{ convertDate(files[oid].createdAt) }}
            </span>
            | <b>Filename:</b>
            {{ files[oid].filename }}
          </small>
        </div>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentFileReference"
      modal-id="file-reference-delete-confirmation-modal"
      modal-name="Confirm to delete File Reference"
      :modal-text="
        'Do you really want do delete the File Reference with name ' +
        currentFileReference.name +
        '?'
      "
      @confirmation="handleDelete(currentFileReference.id)"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import FileReferenceModal from "@/components/references/FileReferenceModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { downloadFile } from "@/utils/download";
import { emitter } from "@/utils/event-bus";
import { dateFormat } from "@/utils/helpers";
import { FileReference, ShepardFile } from "@dlr-shepard/shepard-client";
import Vue from "vue";

interface FileListData {
  fileReferenceList: FileReference[];
  files: { [key: string]: ShepardFile };
  downloadStarted: boolean;
  downloadActive: boolean;
  downloadError: boolean;
  currentFileReference?: FileReference;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default Vue.extend({
  components: {
    CreatedByLine,
    ProcessAlert,
    FileReferenceModal,
    DeleteConfirmationModal,
    GenericName,
  },
  props: {
    currentCollectionId: {
      type: Number,
      required: true,
    },
    currentDataObjectId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      fileReferenceList: [],
      files: {},
      downloadStarted: false,
      downloadActive: false,
      downloadError: false,
      currentFileReference: undefined,
      createdAlert: false,
      deletedAlert: false,
    } as FileListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      FileReferenceService.getAllFileReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.fileReferenceList = response;
          this.fileReferenceList.forEach(reference => {
            if (reference.id) this.getFiles(reference.id);
          });
        })
        .catch(e => {
          const error = "Error while fetching file references: " + e.statusText;
          console.log(error);
        });
    },

    getFiles(id: number) {
      FileReferenceService.getFiles({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReferenceId: id,
      })
        .then(response => {
          const temp: { [key: string]: ShepardFile } = {};
          response.forEach(payload => {
            if (payload?.oid) {
              temp[payload.oid] = payload;
            }
          });
          this.files = { ...this.files, ...temp };
        })
        .catch(e => {
          const error = "Error while fetching files: " + e.statusText;
          console.log(error);
        });
    },

    getFilePayload(fileReferenceId: number, oid: string, filename: string) {
      this.downloadStarted = true;
      this.downloadActive = true;
      FileReferenceService.getFilePayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReferenceId: fileReferenceId,
        oid: oid,
      })
        .then(response => {
          downloadFile(response, filename);
        })
        .catch(e => {
          const error = "Error while fetching file payload: " + e.statusText;
          console.log(error);
          this.downloadStarted = false;
          this.downloadError = true;
        })
        .finally(() => (this.downloadActive = false));
    },

    create(newReference: FileReference) {
      FileReferenceService.createFileReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReference: newReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.fileReferenceList = [response].concat(this.fileReferenceList);
          if (response.id) this.retrieveReferences();
        })
        .catch(e => {
          const error = "Error while creating file reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleDelete(fileReferenceId: number) {
      FileReferenceService.deleteFileReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReferenceId: fileReferenceId,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error = "Error while deleting file reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    convertDate(date: string) {
      return new Date(date).toLocaleString("en-GB", dateFormat);
    },
  },
});
</script>
