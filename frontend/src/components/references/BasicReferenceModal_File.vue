<script setup lang="ts">
import GenericName from "@/components/generic/GenericName.vue";
import CsvPlottingModal from "@/components/payload/CsvPlottingModal.vue";
import CsvTableModal from "@/components/payload/CsvTableModal.vue";
import ImageViewerModal from "@/components/payload/ImageViewerModal.vue";
import TextViewerModal from "@/components/payload/TextViewerModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import JsonFileModal from "@/components/references/JsonFileModal.vue";
import FileReferenceService from "@/services/fileReferenceService";
import { downloadFile } from "@/utils/download";
import { logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  FileReference,
  ResponseError,
  ShepardFile,
} from "@dlr-shepard/backend-client";
import { onMounted, reactive, ref, type PropType } from "vue";

const props = defineProps({
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
    required: true,
  },
});

const getInitialDownloadState = () => ({
  active: false,
  finished: false,
  error: false,
  errorMessage: "",
});

const downloadState = reactive(getInitialDownloadState());
const files = ref(new Map<string, ShepardFile>());
const currentFileOid = ref<string>();

function getFiles() {
  if (!props.fileReference.id) return;
  FileReferenceService.getFiles({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: props.fileReference.id,
  })
    .then(response => {
      response.forEach(payload => {
        if (payload?.oid) {
          files.value.set(payload.oid, payload);
        }
      });
      files.value = new Map([...files.value.entries()]);
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
  downloadState.active = true;
  FileReferenceService.getFilePayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    fileReferenceId: fileReferenceId,
    oid: oid,
  })
    .then(response => {
      downloadFile(response, filename);
      downloadState.finished = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching file payload");
      downloadState.error = true;
      if (e.response.status == 403) {
        downloadState.errorMessage =
          "Authentication Error: No permission to access this file container";
      } else if (e.response.status == 404) {
        downloadState.errorMessage =
          "Not Found: File no longer exists in the container";
      }
    })
    .finally(() => (downloadState.active = false));
}

onMounted(() => {
  getFiles();
  currentFileOid.value = undefined;
  Object.assign(downloadState, getInitialDownloadState());
});
</script>

<template>
  <div>
    <ProcessAlert
      process-name="Download"
      :process-active="downloadState.active"
      :process-finished="downloadState.finished"
      :process-error="downloadState.error"
      :process-error-message="downloadState.errorMessage"
      @success-message-dismissed="downloadState.finished = false"
      @error-message-dismissed="downloadState.error = false"
    />
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

    <b-list-group class="list">
      <b-list-group-item
        v-for="(oid, index) in fileReference?.fileOids"
        :key="index"
      >
        <div v-if="files.has(oid)">
          <b>
            <GenericName
              :word-count="30"
              :name="files.get(oid)?.filename || ''"
            />
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
              v-if="
                files.get(oid)?.filename?.match(/\.(jpg|jpeg|png|gif|bmp)$/i)
              "
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
                files.get(oid)?.filename?.match(/\.(txt|md|yaml|toml)$/i)
              "
              v-b-modal.text-viewer-modal
              v-b-tooltip.hover
              variant="primary"
              title="Show Text"
              @click="currentFileOid = oid"
            >
              <EyeIcon />
            </b-button>
            <b-button
              v-else-if="files.get(oid)?.filename?.match(/\.(csv)$/i)"
              v-b-modal.csv-table-modal
              v-b-tooltip.hover
              variant="primary"
              title="Show Text"
              @click="currentFileOid = oid"
            >
              <EyeIcon />
            </b-button>
            <b-button
              v-else-if="files.get(oid)?.filename?.match(/\.(json)$/i)"
              v-b-modal.json-file-modal
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
              :disabled="!files.get(oid)?.filename?.match(/\.csv$/i)"
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
              :disabled="downloadState.active"
              @click="
                getFilePayload(fileReference?.id, oid, files.get(oid)?.filename)
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
        <div v-if="files.get(oid)?.createdAt">
          created at: {{ convertDate(files.get(oid)?.createdAt) }}
        </div>
      </b-list-group-item>
    </b-list-group>

    <ImageViewerModal
      v-if="fileReference && currentFileOid"
      modal-id="image-viewer-modal"
      :modal-name="files.get(currentFileOid)?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <TextViewerModal
      v-if="fileReference && currentFileOid"
      modal-id="text-viewer-modal"
      :modal-name="files.get(currentFileOid)?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <JsonFileModal
      v-if="fileReference && currentFileOid"
      modal-id="json-file-modal"
      :modal-name="files.get(currentFileOid)?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <CsvPlottingModal
      v-if="fileReference && currentFileOid"
      modal-id="plotting-modal"
      :modal-name="files.get(currentFileOid)?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
    <CsvTableModal
      v-if="fileReference && currentFileOid"
      modal-id="csv-table-modal"
      :modal-name="files.get(currentFileOid)?.filename"
      :container-id="fileReference.fileContainerId"
      :oid="currentFileOid"
    />
  </div>
</template>
