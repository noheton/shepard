<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import FilePlottingModal from "@/components/references/FilePlottingModal.vue";
import FileReferenceModal from "@/components/references/FileReferenceModal.vue";
import ImageViewerModal from "@/components/references/ImageViewerModal.vue";
import TextViewerModal from "@/components/references/TextViewerModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { dateFormat } from "@/utils/helpers";
import type {
  FileReference,
  ResponseError,
  ShepardFile,
} from "@dlr-shepard/shepard-client";
import { onMounted, ref } from "vue";

const props = defineProps({
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
});

const fileReferenceList = ref<FileReference[]>();
const files = ref<{ [key: string]: ShepardFile }>({});
const downloadFinished = ref(false);
const downloadActive = ref(false);
const downloadError = ref(false);
const downloadErrorMessage = ref("");
const currentFileReference = ref<FileReference>();
const currentFileOid = ref<string>();
const createdAlert = ref(false);
const deletedAlert = ref(false);
const currentFileName = ref<string>();
const csvFileData = ref<string>();

function retrieveReferences() {
  FileReferenceService.getAllFileReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      fileReferenceList.value = response;
      fileReferenceList.value.forEach(reference => {
        if (reference.id) getFiles(reference.id);
      });
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching file references");
    });
}

function getFiles(id: number) {
  FileReferenceService.getFiles({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: id,
  })
    .then(response => {
      const temp: { [key: string]: ShepardFile } = {};
      response.forEach(payload => {
        if (payload?.oid) {
          temp[payload.oid] = payload;
        }
      });
      files.value = { ...files.value, ...temp };
    })
    .catch(e => {
      logError(e as ResponseError, "fetching files");
    });
}

function getFilePayload(
  fileReferenceId: number,
  oid: string,
  filename?: string,
) {
  downloadActive.value = true;
  FileReferenceService.getFilePayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: fileReferenceId,
    oid: oid,
  })
    .then(response => {
      downloadFile(response, filename);
      downloadFinished.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      downloadError.value = true;
      if (e.response.status == 403) {
        downloadErrorMessage.value =
          "Authentication Error: No permission to access this file container";
      } else if (e.response.status == 404) {
        downloadErrorMessage.value =
          "Not Found: File no longer exists in the container";
      }
    })
    .finally(() => (downloadActive.value = false));
}

function handlePlotCsvData(
  fileReferenceId: number,
  oid: string,
  filename?: string,
) {
  currentFileName.value = filename || "File";
  const blobReader = new FileReader();
  downloadActive.value = true;
  FileReferenceService.getFilePayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: fileReferenceId,
    oid: oid,
  })
    .then(response => {
      blobReader.readAsText(response, "utf8");
      blobReader.addEventListener("load", () => {
        if (typeof blobReader.result === "string") {
          csvFileData.value = blobReader.result;
        }
      });
      downloadFinished.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      downloadError.value = true;
    })
    .finally(() => (downloadActive.value = false));
}

function create(newReference: FileReference) {
  FileReferenceService.createFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReference: newReference,
  })
    .then(response => {
      createdAlert.value = true;
      fileReferenceList.value = [response].concat(
        fileReferenceList.value || [],
      );
      if (response.id) retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "creating file reference");
    });
}

function handleDelete() {
  if (!currentFileReference.value?.id) return;
  FileReferenceService.deleteFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: currentFileReference.value.id,
  })
    .then(() => {
      deletedAlert.value = true;
      retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting file reference");
    });
}

function convertDate(date: Date | undefined | null) {
  if (date) return new Date(date).toLocaleString("en-GB", dateFormat);
}

function setCurrentFileReference(fileReference: FileReference, oid: string) {
  currentFileReference.value = fileReference;
  currentFileOid.value = oid;
}

onMounted(() => {
  retrieveReferences();
});
</script>

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
              v-if="files[oid].filename?.match(/\.csv$/i)"
              v-b-modal.plotting_modal
              class="float-right"
              title="Plot Data"
              variant="primary"
              size="sm"
              style="font-size: 0.6em"
              @click="
                if (fileReference.id)
                  handlePlotCsvData(fileReference.id, oid, files[oid].filename);
              "
            >
              <PlottingIcon />
            </b-button>
            <b-button
              v-if="files[oid].filename?.match(/\.(jpg|jpeg|png|gif|bmp)$/i)"
              v-b-modal.image-viewer-modal
              v-b-tooltip.hover
              class="float-right"
              size="sm"
              variant="primary"
              title="Show Image"
              style="font-size: 0.6em"
              @click="setCurrentFileReference(fileReference, oid)"
            >
              <EyeIcon />
            </b-button>
            <b-button
              v-if="files[oid].filename?.match(/\.(txt|md|json|yaml|toml)$/i)"
              v-b-modal.text-viewer-modal
              v-b-tooltip.hover
              class="float-right"
              size="sm"
              variant="primary"
              title="Show Text"
              style="font-size: 0.6em"
              @click="setCurrentFileReference(fileReference, oid)"
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

    <TextViewerModal
      v-if="currentFileReference && currentFileOid"
      modal-id="text-viewer-modal"
      :modal-name="files[currentFileOid]?.filename"
      :container-id="currentFileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <FilePlottingModal
      modal-id="plotting_modal"
      :modal-name="currentFileName"
      :csv-data="csvFileData"
    />
  </div>
</template>
