<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import FilePlottingModal from "@/components/payload/FilePlottingModal.vue";
import ImageViewerModal from "@/components/payload/ImageViewerModal.vue";
import TextViewerModal from "@/components/payload/TextViewerModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  FileReference,
  ResponseError,
  ShepardFile,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, ref, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "file-reference-modal",
  },
  modalName: {
    type: String,
    default: "File Reference",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  fileReference: {
    type: Object as PropType<FileReference>,
    default: undefined,
  },
});

const emit = defineEmits(["reference-deleted"]);

const files = ref<{ [key: string]: ShepardFile | undefined }>({});
const downloadFinished = ref(false);
const downloadActive = ref(false);
const downloadError = ref(false);
const downloadErrorMessage = ref("");
const currentFileOid = ref<string>();
const csvFileData = ref<string>();

function reset() {
  files.value = {};
  downloadFinished.value = false;
  downloadActive.value = false;
  downloadError.value = false;
  downloadErrorMessage.value = "";
  currentFileOid.value = undefined;
  csvFileData.value = undefined;
  getFiles();
}

function getFiles() {
  if (!props.fileReference.id) return;
  FileReferenceService.getFiles({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: props.fileReference.id,
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
  fileReferenceId?: number,
  oid?: string,
  filename?: string,
) {
  if (!fileReferenceId || !oid) return;
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

const vm = getCurrentInstance();
function handleDelete() {
  if (!props.fileReference.id) return;
  FileReferenceService.deleteFileReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: props.fileReference.id,
  })
    .then(() => {
      emit("reference-deleted");
      vm?.proxy.$bvModal.hide(props.modalId);
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting file reference");
    });
}
</script>

<template>
  <b-modal
    :id="modalId"
    :title="modalName"
    size="lg"
    lazy
    ok-only
    @show="reset()"
  >
    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-finished="downloadFinished"
      :process-error="downloadError"
      :process-error-message="downloadErrorMessage"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />
    <div class="mb-4">
      <b-button
        v-b-modal.file-reference-delete-confirmation-modal
        v-b-tooltip.hover
        class="float-right"
        title="Delete"
        variant="info"
      >
        <DeleteIcon />
      </b-button>

      ID: {{ fileReference?.id }} |
      <span v-if="fileReference?.fileContainerId != -1">
        <b-link
          :to="{
            name: 'Files',
            params: { fileId: fileReference?.fileContainerId },
          }"
        >
          Container: {{ fileReference?.fileContainerId }}
        </b-link>
      </span>
      <span v-else class="text-danger">Container: Deleted</span>
      <CreatedByLine
        :created-by="fileReference?.createdBy"
        :created-at="fileReference?.createdAt"
      />
    </div>
    <b-list-group class="list">
      <b-list-group-item
        v-for="(oid, index) in fileReference?.fileOids"
        :key="index"
      >
        <div v-if="files[oid]">
          <b>
            <GenericName :word-count="30" :name="files[oid]?.filename || ''" />
          </b>
          | Oid:
          {{ oid }}
          <span v-if="fileReference?.fileContainerId == -1">
            | <span class="text-danger"> Deleted </span>
          </span>

          <b-button-group
            v-if="fileReference?.fileContainerId != -1"
            class="float-right"
          >
            <b-button
              v-if="files[oid]?.filename?.match(/\.(jpg|jpeg|png|gif|bmp)$/i)"
              v-b-modal.image-viewer-modal
              v-b-tooltip.hover
              variant="primary"
              title="Show Image"
              @click="currentFileOid = oid"
            >
              <EyeIcon />
            </b-button>
            <b-button
              v-else-if="
                files[oid]?.filename?.match(/\.(txt|md|json|yaml|toml|csv)$/i)
              "
              v-b-modal.text-viewer-modal
              v-b-tooltip.hover
              variant="primary"
              title="Show Text"
              @click="currentFileOid = oid"
            >
              <EyeIcon />
            </b-button>
            <b-button v-else variant="primary" :disabled="true">
              <EyeIcon />
            </b-button>

            <b-button
              v-b-modal.plotting-modal
              v-b-tooltip.hover
              :disabled="!files[oid]?.filename?.match(/\.csv$/i)"
              variant="secondary"
              title="Plot Data"
              @click="currentFileOid = oid"
            >
              <PlottingIcon />
            </b-button>

            <b-button
              v-b-tooltip.hover
              variant="secondary"
              title="Download File"
              :disabled="downloadActive"
              @click="
                getFilePayload(fileReference?.id, oid, files[oid]?.filename)
              "
            >
              <DownloadIcon />
            </b-button>
          </b-button-group>

          <b-button-group v-else class="float-right">
            <b-button variant="primary" :disabled="true">
              <EyeIcon />
            </b-button>

            <b-button variant="secondary" :disabled="true">
              <PlottingIcon />
            </b-button>

            <b-button variant="secondary" :disabled="true">
              <DownloadIcon />
            </b-button>
          </b-button-group>
        </div>
        <div v-if="files[oid]?.createdAt">
          created at: {{ convertDate(files[oid]?.createdAt) }}
        </div>
      </b-list-group-item>
    </b-list-group>

    <ImageViewerModal
      v-if="fileReference && currentFileOid"
      modal-id="image-viewer-modal"
      :modal-name="files[currentFileOid]?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <TextViewerModal
      v-if="fileReference && currentFileOid"
      modal-id="text-viewer-modal"
      :modal-name="files[currentFileOid]?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <FilePlottingModal
      v-if="fileReference && currentFileOid"
      modal-id="plotting-modal"
      :modal-name="files[currentFileOid]?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <DeleteConfirmationModal
      v-if="props.fileReference"
      modal-id="file-reference-delete-confirmation-modal"
      modal-name="Confirm to delete File Reference"
      :modal-text="
        'Do you really want do delete the File Reference with name ' +
        props.fileReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </b-modal>
</template>
