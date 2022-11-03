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
      variant="info"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-finished="downloadFinished"
      :process-error="downloadError"
      :process-error-message="downloadErrorMessage"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />

    <b-button v-b-modal.create-file-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <FileReferenceModal
      modal-id="create-file-ref-modal"
      modal-name="Create File Reference"
      @create="create($event)"
    />
    <div v-if="fileReferenceList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(fileReference, index) in fileReferenceList"
        :key="index"
      >
        <div>
          <b><GenericName :name="fileReference.name || ''" /></b> | ID:
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
            variant="info"
            @click="currentFileReference = fileReference"
          >
            <DeleteIcon />
          </b-button>
        </div>
        <CreatedByLine
          :created-by="fileReference.createdBy"
          :created-at="fileReference.createdAt"
        />
        <div v-for="(oid, jndex) in fileReference.fileOids" :key="jndex">
          <small v-if="files[oid]">
            <span v-if="fileReference.fileContainerId != -1">
              <b-link
                :disabled="downloadActive"
                @click="
                  if (fileReference.id)
                    getFilePayload(fileReference.id, oid, files[oid].filename);
                "
              >
                <DownloadIcon />
              </b-link>
            </span>
            <span v-else><DownloadIcon variant="danger" /></span>
            <b> Oid:</b> <tt>{{ oid }}</tt>
            <span v-if="files[oid].createdAt">
              | <b>Created at:</b>
              {{ convertDate(files[oid].createdAt) }}
            </span>
            | <b>Filename:</b>
            {{ files[oid].filename }}
            <b-button
              v-if="files[oid].filename?.match(/\.(jpg|jpeg|png|gif)$/i)"
              v-b-modal.image-viewer-modal
              v-b-tooltip.hover
              class="float-right"
              size="sm"
              variant="primary"
              title="Show Image"
              style="font-size: 0.6em"
              @click="showImageClicked(fileReference, oid)"
            >
              <EyeIcon />
            </b-button>
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
      @confirmation="handleDelete()"
    />

    <ImageViewerModal
      v-if="currentFileReference && currentFileOid"
      modal-id="image-viewer-modal"
      :modal-name="files[currentFileOid]?.filename"
      :container-id="currentFileReference.fileContainerId"
      :oid="currentFileOid"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import ImageViewerModal from "@/components/generic/ImageViewerModal.vue";
import Loading from "@/components/generic/Loading.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import FileReferenceModal from "@/components/references/FileReferenceModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { dateFormat } from "@/utils/helpers";
import type {
  FileReference,
  ResponseError,
  ShepardFile,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface FileListData {
  fileReferenceList?: FileReference[];
  files: { [key: string]: ShepardFile };
  downloadFinished: boolean;
  downloadActive: boolean;
  downloadError: boolean;
  downloadErrorMessage: string;
  currentFileReference?: FileReference;
  currentFileOid?: string;
  createdAlert: boolean;
  deletedAlert: boolean;
}

export default defineComponent({
  components: {
    CreatedByLine,
    ProcessAlert,
    FileReferenceModal,
    DeleteConfirmationModal,
    GenericName,
    ImageViewerModal,
    Loading,
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
      fileReferenceList: undefined,
      files: {},
      downloadFinished: false,
      downloadActive: false,
      downloadError: false,
      downloadErrorMessage: "",
      currentFileReference: undefined,
      currentFileOid: undefined,
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
          handleError(e as ResponseError, "fetching file references");
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
          logError(e as ResponseError, "fetching files");
        });
    },

    getFilePayload(fileReferenceId: number, oid: string, filename?: string) {
      this.downloadActive = true;
      FileReferenceService.getFilePayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReferenceId: fileReferenceId,
        oid: oid,
      })
        .then(response => {
          downloadFile(response, filename);
          this.downloadFinished = true;
        })
        .catch(e => {
          logError(e as ResponseError, "fetching file payload");
          this.downloadError = true;
          if (e.response.status == 403) {
            this.downloadErrorMessage =
              "Authentication Error: No permission to access this file container";
          } else if (e.response.status == 404) {
            this.downloadErrorMessage =
              "Not Found: File no longer exists in the container";
          }
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
          this.fileReferenceList = [response].concat(
            this.fileReferenceList || [],
          );
          if (response.id) this.retrieveReferences();
        })
        .catch(e => {
          handleError(e as ResponseError, "creating file reference");
        });
    },

    handleDelete() {
      if (!this.currentFileReference?.id) return;
      FileReferenceService.deleteFileReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        fileReferenceId: this.currentFileReference.id,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          handleError(e as ResponseError, "deleting file reference");
        });
    },
    convertDate(date: Date | undefined | null) {
      if (date) return new Date(date).toLocaleString("en-GB", dateFormat);
    },
    showImageClicked(fileReference: FileReference, oid: string) {
      this.currentFileReference = fileReference;
      this.currentFileOid = oid;
    },
  },
});
</script>
