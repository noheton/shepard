<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import TimeseriesPlottingModal from "@/components/payload/TimeseriesPlottingModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";

import type {
  ResponseError,
  TimeseriesPayload,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { getCurrentInstance, ref, type PropType } from "vue";

const props = defineProps({
  modalId: {
    type: String,
    default: "timeseries-reference-modal",
  },
  modalName: {
    type: String,
    default: "Timeseries Reference",
  },
  currentCollectionId: {
    type: Number,
    required: true,
  },
  currentDataObjectId: {
    type: Number,
    required: true,
  },
  timeseriesReference: {
    type: Object as PropType<TimeseriesReference>,
    default: undefined,
  },
});

const emit = defineEmits(["reference-deleted"]);

const currentStructuredDataOid = ref<string>();
const currentTimeseriesReference = ref<TimeseriesReference>();
const timeseriesDatas = ref<{ [key: string]: TimeseriesPayload }>({});
const currentTimeseriesPayload = ref<TimeseriesPayload[]>();

const downloadFinished = ref(false);
const downloadActive = ref(false);
const downloadError = ref(false);
const downloadErrorMessage = ref<string>("");
const plottingError = ref(false);
const plottingErrorMessage = ref<string>("");

function reset() {
  timeseriesDatas.value = {};
  currentStructuredDataOid.value = undefined;
  fetchTimeseriesPayload();
}

function fetchTimeseriesPayload() {
  if (!props.timeseriesReference.id) return;
  TimeseriesReferenceService.getTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: props.timeseriesReference.id,
  })
    .then(response => {
      currentTimeseriesPayload.value = response;
    })
    .catch(e => {
      currentTimeseriesPayload.value = [];
      logError(e as ResponseError, "fetching timeseries payload");
      plottingError.value = true;
      if (e.response.status == 403) {
        plottingErrorMessage.value =
          "Authentication Error: No permission to access this timeseries container";
      }
    });
}

function downloadCsv(referenceId: number, referenceName: string) {
  downloadActive.value = true;
  TimeseriesReferenceService.exportTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: referenceId,
  })
    .then(response => {
      downloadFile(response, referenceName + ".csv");
      downloadFinished.value = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching timeseries payload");
      downloadError.value = true;
      if (e.response.status == 403) {
        downloadErrorMessage.value =
          "Authentication Error: No permission to access this timeseries container";
      }
    })
    .finally(() => (downloadActive.value = false));
}

function handlePlotData(timeseriesItem: TimeseriesReference) {
  if (timeseriesItem.id) fetchTimeseriesPayload();
  currentTimeseriesReference.value = timeseriesItem;
}

const vm = getCurrentInstance();
function handleDelete() {
  if (!props.timeseriesReference.id) return;
  TimeseriesReferenceService.deleteTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: props.timeseriesReference.id,
  })
    .then(() => {
      emit("reference-deleted");
      vm?.proxy.$bvModal.hide(props.modalId);
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries reference");
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
      process-name="Plotting"
      :process-error="plottingError"
      :process-error-message="plottingErrorMessage"
      @error-message-dismissed="plottingError = false"
    />

    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-started="downloadFinished"
      :process-error="downloadError"
      :process-error-message="downloadErrorMessage"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />

    <div v-if="timeseriesReference" class="mb-4">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.plotting_modal
          v-b-tooltip.hover
          title="Plotting"
          variant="primary"
          :disabled="timeseriesReference.timeseriesContainerId == -1"
          @click="handlePlotData(timeseriesReference)"
        >
          <PlottingIcon />
        </b-button>

        <b-button
          v-b-tooltip.hover
          title="Download"
          variant="secondary"
          :disabled="
            downloadActive || timeseriesReference.timeseriesContainerId == -1
          "
          @click="
            if (timeseriesReference.id)
              downloadCsv(
                timeseriesReference.id,
                timeseriesReference.name || '',
              );
          "
        >
          <DownloadIcon />
        </b-button>
        <b-button
          v-b-modal.timeseries-reference-delete-confirmation-modal
          v-b-tooltip.hover
          title="Delete"
          variant="info"
          @click="currentTimeseriesReference = timeseriesReference"
        >
          <DeleteIcon />
        </b-button>
      </b-button-group>

      ID: {{ timeseriesReference?.id }} |
      <span v-if="timeseriesReference?.timeseriesContainerId != -1">
        <b-link
          :to="{
            name: 'Files',
            params: {
              fileId: timeseriesReference?.timeseriesContainerId,
            },
          }"
        >
          Container: {{ timeseriesReference?.timeseriesContainerId }}
        </b-link>
      </span>
      <span v-else class="text-danger">Container: Deleted</span>

      <CreatedByLine
        :created-by="timeseriesReference?.createdBy"
        :created-at="timeseriesReference?.createdAt"
      />
      <small>
        <b>start:</b>
        {{ convertDate(new Date(timeseriesReference.start / 1e6)) }}
        |
        <b>end:</b>
        {{ convertDate(new Date(timeseriesReference.start / 1e6)) }}
      </small>

      <b-table
        class="mt-4"
        striped
        hover
        small
        :items="timeseriesReference.timeseries"
      >
      </b-table>
    </div>

    <TimeseriesPlottingModal
      v-if="
        currentTimeseriesReference && currentTimeseriesPayload && !plottingError
      "
      modal-id="plotting_modal"
      :modal-name="currentTimeseriesReference.name || undefined"
      :timeseries-payload-list="currentTimeseriesPayload"
      :timeseries-start-time="currentTimeseriesReference.start"
    />

    <DeleteConfirmationModal
      v-if="props.timeseriesReference"
      modal-id="timeseries-reference-delete-confirmation-modal"
      modal-name="Confirm to delete Timeseries Reference"
      :modal-text="
        'Do you really want do delete the Timeseries Reference with name ' +
        props.timeseriesReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />
  </b-modal>
</template>
