<script setup lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import TimeseriesPlottingModal from "@/components/payload/TimeseriesPlottingModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import CreateTimeseriesReferenceModal from "@/components/references/CreateTimeseriesReferenceModal.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { downloadFile } from "@/utils/download";
import { handleError, logError } from "@/utils/error-handling";
import { dateFormat } from "@/utils/helpers";
import type {
  ResponseError,
  TimeseriesPayload,
  TimeseriesReference,
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

const emit = defineEmits(["reference-count-changed"]);

const timeseriesList = ref<TimeseriesReference[]>();
const currentTimeseriesReference = ref<TimeseriesReference>();
const currentTimeseriesPayload = ref<TimeseriesPayload[]>();

const downloadFinished = ref(false);
const downloadActive = ref(false);
const downloadError = ref(false);
const downloadErrorMessage = ref<string>("");
const plottingError = ref(false);
const plottingErrorMessage = ref<string>("");
const createdAlert = ref(false);
const deletedAlert = ref(false);

function retrieveReferences() {
  TimeseriesReferenceService.getAllTimeseriesReferences({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
  })
    .then(response => {
      timeseriesList.value = response;
      emit("reference-count-changed", timeseriesList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "fetching timeseries references");
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
  if (timeseriesItem.id) fetchTimeseriesPayload(timeseriesItem.id);
  currentTimeseriesReference.value = timeseriesItem;
}

function fetchTimeseriesPayload(referenceId: number) {
  TimeseriesReferenceService.getTimeseriesPayload({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: referenceId,
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

function handleCreate(timeseriesReference: TimeseriesReference) {
  TimeseriesReferenceService.createTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReference: timeseriesReference,
  })
    .then(response => {
      createdAlert.value = true;
      timeseriesList.value = [response].concat(timeseriesList.value || []);
      emit("reference-count-changed", timeseriesList.value.length);
    })
    .catch(e => {
      handleError(e as ResponseError, "creating timeseries reference");
    });
}

function handleDelete() {
  if (!currentTimeseriesReference.value?.id) return;
  TimeseriesReferenceService.deleteTimeseriesReference({
    collectionId: props.currentCollectionId,
    dataObjectId: props.currentDataObjectId,
    timeseriesReferenceId: currentTimeseriesReference.value.id,
  })
    .then(() => {
      deletedAlert.value = true;
      retrieveReferences();
    })
    .catch(e => {
      handleError(e as ResponseError, "deleting timeseries reference");
    });
}

function convertDate(date: number) {
  return new Date(date).toLocaleString("en-GB", dateFormat);
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
      :process-started="downloadFinished"
      :process-error="downloadError"
      :process-error-message="downloadErrorMessage"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />
    <ProcessAlert
      process-name="Plotting"
      :process-error="plottingError"
      :process-error-message="plottingErrorMessage"
      @error-message-dismissed="plottingError = false"
    />

    <b-button v-b-modal.create-time-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <CreateTimeseriesReferenceModal
      modal-id="create-time-ref-modal"
      modal-name="Create Time Reference"
      @create="handleCreate($event)"
    />

    <div v-if="timeseriesList == undefined"><Loading /></div>
    <b-list-group v-else>
      <b-list-group-item
        v-for="(timeseriesItem, index) in timeseriesList"
        :key="index"
      >
        <div>
          <b><GenericName :name="timeseriesItem.name || ''" /></b> | ID:
          {{ timeseriesItem.id }} |
          <span v-if="timeseriesItem.timeseriesContainerId != -1">
            <b-link
              :to="{
                name: 'Timeseries',
                params: { timeseriesId: timeseriesItem.timeseriesContainerId },
              }"
            >
              Container: {{ timeseriesItem.timeseriesContainerId }}
            </b-link>
          </span>
          <span v-else class="text-danger">Container: Deleted</span>

          <b-button-group class="float-right">
            <b-button
              v-b-modal.plotting_modal
              v-b-tooltip.hover
              title="Plotting"
              variant="primary"
              :disabled="timeseriesItem.timeseriesContainerId == -1"
              @click="handlePlotData(timeseriesItem)"
            >
              <PlottingIcon />
            </b-button>

            <b-button
              v-b-tooltip.hover
              title="Download"
              variant="secondary"
              :disabled="
                downloadActive || timeseriesItem.timeseriesContainerId == -1
              "
              @click="
                if (timeseriesItem.id)
                  downloadCsv(timeseriesItem.id, timeseriesItem.name || '');
              "
            >
              <DownloadIcon />
            </b-button>
            <b-button
              v-b-modal.timeseries-reference-delete-confirmation-modal
              v-b-tooltip.hover
              title="Delete"
              variant="info"
              @click="currentTimeseriesReference = timeseriesItem"
            >
              <DeleteIcon />
            </b-button>
          </b-button-group>
        </div>
        <CreatedByLine
          :created-by="timeseriesItem.createdBy"
          :created-at="timeseriesItem.createdAt"
        />
        <small>
          <b>start:</b>
          {{ convertDate(timeseriesItem.start / 1e6) }}
          |
          <b>end:</b>
          {{ convertDate(timeseriesItem.end / 1e6) }}
        </small>
        <b-table striped hover small :items="timeseriesItem.timeseries">
        </b-table>
      </b-list-group-item>
    </b-list-group>

    <DeleteConfirmationModal
      v-if="currentTimeseriesReference"
      modal-id="timeseries-reference-delete-confirmation-modal"
      modal-name="Confirm to delete URI Reference"
      :modal-text="
        'Do you really want do delete the URI Reference with name ' +
        currentTimeseriesReference.name +
        '?'
      "
      @confirmation="handleDelete()"
    />

    <TimeseriesPlottingModal
      v-if="
        currentTimeseriesReference && currentTimeseriesPayload && !plottingError
      "
      modal-id="plotting_modal"
      :modal-name="currentTimeseriesReference.name || undefined"
      :timeseries-payload-list="currentTimeseriesPayload"
      :timeseries-start-time="currentTimeseriesReference.start"
    />
  </div>
</template>
