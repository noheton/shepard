<script setup lang="ts">
import TimeseriesPlottingModal from "@/components/payload/TimeseriesPlottingModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { downloadFile } from "@/utils/download";
import { logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type {
  ResponseError,
  TimeseriesPayload,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
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
  timeseriesReference: {
    type: Object as PropType<TimeseriesReference>,
    required: true,
  },
});

const getInitialState = () => ({
  active: false,
  finished: false,
  error: false,
  errorMessage: "",
  plottingError: false,
  plottingErrorMessage: "",
});

const currentTimeseriesPayload = ref<TimeseriesPayload[]>();
const internalState = reactive(getInitialState());

function fetchTimeseriesPayload() {
  if (!props.timeseriesReference?.id) return;
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
      internalState.plottingError = true;
      if (e.response.status == 403) {
        internalState.plottingErrorMessage =
          "Authentication Error: No permission to access this timeseries container";
      }
    });
}

function downloadCsv(referenceId: number, referenceName: string) {
  internalState.active = true;
  TimeseriesReferenceService.exportTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: referenceId,
  })
    .then(response => {
      downloadFile(response, referenceName + ".csv");
      internalState.finished = true;
    })
    .catch(e => {
      logError(e as ResponseError, "fetching timeseries payload");
      internalState.error = true;
      if (e.response.status == 403) {
        internalState.errorMessage =
          "Authentication Error: No permission to access this timeseries container";
      }
    })
    .finally(() => (internalState.active = false));
}

onMounted(() => {
  fetchTimeseriesPayload();
  Object.assign(internalState, getInitialState());
});
</script>

<template>
  <div>
    <ProcessAlert
      process-name="Plotting"
      :process-error="internalState.plottingError"
      :process-error-message="internalState.plottingErrorMessage"
      @error-message-dismissed="internalState.plottingError = false"
    />

    <ProcessAlert
      process-name="Download"
      :process-active="internalState.active"
      :process-started="internalState.finished"
      :process-error="internalState.error"
      :process-error-message="internalState.errorMessage"
      @success-message-dismissed="internalState.finished = false"
      @error-message-dismissed="internalState.error = false"
    />

    <div v-if="props.timeseriesReference" class="mb-4">
      <b-button-group class="float-right">
        <b-button
          v-b-modal.plotting_modal
          v-b-tooltip.hover
          title="Plotting"
          variant="primary"
          :disabled="props.timeseriesReference.timeseriesContainerId == -1"
        >
          <PlottingIcon />
        </b-button>

        <b-button
          v-b-tooltip.hover
          title="Download"
          variant="secondary"
          :disabled="
            internalState.active ||
            props.timeseriesReference.timeseriesContainerId == -1
          "
          @click="
            if (props.timeseriesReference.id)
              downloadCsv(
                props.timeseriesReference.id,
                props.timeseriesReference.name || '',
              );
          "
        >
          <DownloadIcon />
        </b-button>
      </b-button-group>

      <span v-if="props.timeseriesReference?.timeseriesContainerId != -1">
        <b-link
          :to="{
            name: 'Files',
            params: {
              fileId: props.timeseriesReference?.timeseriesContainerId,
            },
          }"
        >
          Container: {{ props.timeseriesReference?.timeseriesContainerId }}
        </b-link>
      </span>
      <span v-else class="text-danger">Container: Deleted</span>
      <div>
        <small>
          <b>start:</b>
          {{ convertDate(new Date(props.timeseriesReference.start / 1e6)) }}
          |
          <b>end:</b>
          {{ convertDate(new Date(props.timeseriesReference.start / 1e6)) }}
        </small>
      </div>

      <b-table
        striped
        hover
        small
        :items="props.timeseriesReference.timeseries"
      >
      </b-table>
    </div>

    <TimeseriesPlottingModal
      v-if="props.timeseriesReference && currentTimeseriesPayload"
      modal-id="plotting_modal"
      :modal-name="props.timeseriesReference.name || undefined"
      :timeseries-payload-list="currentTimeseriesPayload"
      :timeseries-start-time="props.timeseriesReference.start"
    />
  </div>
</template>
