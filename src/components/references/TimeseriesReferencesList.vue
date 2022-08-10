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
      variant="dark"
      @dismissed="deletedAlert = false"
    >
      Successfully deleted
    </b-alert>
    <ProcessAlert
      process-name="Download"
      :process-active="downloadActive"
      :process-started="downloadFinished"
      :process-error="downloadError"
      @success-message-dismissed="downloadFinished = false"
      @error-message-dismissed="downloadError = false"
    />

    <b-button v-b-modal.create-time-ref-modal class="mb-3" variant="primary">
      Create new Reference
    </b-button>

    <TimeseriesReferenceModal
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
              title="Plot Data"
              variant="primary"
              :disabled="timeseriesItem.timeseriesContainerId == -1"
              @click="handlePlotData(timeseriesItem)"
            >
              <PlottingIcon />
            </b-button>

            <b-button
              v-b-tooltip.hover
              title="Download"
              variant="light"
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
              variant="dark"
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
      v-if="currentTimeseriesReference"
      modal-id="plotting_modal"
      :modal-name="currentTimeseriesReference.name || undefined"
      :timeseries-payload-list="currentTimeseriesPayload"
      :timeseries-start-time="currentTimeseriesReference.start"
    />
  </div>
</template>

<script lang="ts">
import DeleteConfirmationModal from "@/components/DeleteConfirmationModal.vue";
import CreatedByLine from "@/components/generic/CreatedByLine.vue";
import GenericName from "@/components/generic/GenericName.vue";
import Loading from "@/components/generic/Loading.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import TimeseriesPlottingModal from "@/components/references/TimeseriesPlottingModal.vue";
import TimeseriesReferenceModal from "@/components/references/TimeseriesReferenceModal.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import { downloadFile } from "@/utils/download";
import { emitter } from "@/utils/event-bus";
import { dateFormat } from "@/utils/helpers";
import type {
  TimeseriesPayload,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { defineComponent } from "vue";

interface TimeseriesListData {
  timeseriesList?: TimeseriesReference[];
  downloadFinished: boolean;
  downloadActive: boolean;
  downloadError: boolean;
  currentTimeseriesReference?: TimeseriesReference;
  createdAlert: boolean;
  deletedAlert: boolean;
  currentTimeseriesPayload: TimeseriesPayload[];
}

export default defineComponent({
  components: {
    CreatedByLine,
    ProcessAlert,
    TimeseriesReferenceModal,
    TimeseriesPlottingModal,
    DeleteConfirmationModal,
    GenericName,
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
      timeseriesList: undefined,
      downloadFinished: false,
      downloadActive: false,
      downloadError: false,
      currentTimeseriesReference: undefined,
      createdAlert: false,
      deletedAlert: false,
      currentTimeseriesPayload: [],
    } as TimeseriesListData;
  },
  mounted() {
    this.retrieveReferences();
  },
  methods: {
    retrieveReferences() {
      TimeseriesReferenceService.getAllTimeseriesReferences({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
      })
        .then(response => {
          this.timeseriesList = response;
        })
        .catch(e => {
          const error =
            "Error while fetching timeseries references: " + e.statusText;
          console.log(error);
        });
    },
    downloadCsv(referenceId: number, referenceName: string) {
      this.downloadActive = true;
      TimeseriesReferenceService.exportTimeseriesPayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        timeseriesReferenceId: referenceId,
      })
        .then(response => {
          downloadFile(response, referenceName + ".csv");
          this.downloadFinished = true;
        })
        .catch(e => {
          const error =
            "Error while fetching timeseries payload: " + e.statusText;
          console.log(error);
          this.downloadError = true;
        })
        .finally(() => (this.downloadActive = false));
    },
    handlePlotData(timeseriesItem: TimeseriesReference) {
      if (timeseriesItem.id) this.fetchTimeseriePayload(timeseriesItem.id);
      this.currentTimeseriesReference = timeseriesItem;
    },
    fetchTimeseriePayload(referenceId: number) {
      TimeseriesReferenceService.getTimeseriesPayload({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        timeseriesReferenceId: referenceId,
      })
        .then(response => {
          this.currentTimeseriesPayload = response;
        })
        .catch(e => {
          this.currentTimeseriesPayload = [];
          const error =
            "Error while fetching timeseries payload: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    handleDelete() {
      if (!this.currentTimeseriesReference?.id) return;
      TimeseriesReferenceService.deleteTimeseriesReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        timeseriesReferenceId: this.currentTimeseriesReference.id,
      })
        .then(() => {
          this.deletedAlert = true;
          this.retrieveReferences();
        })
        .catch(e => {
          const error =
            "Error while deleting timeseries reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },

    handleCreate(timeseriesReference: TimeseriesReference) {
      TimeseriesReferenceService.createTimeseriesReference({
        collectionId: this.currentCollectionId,
        dataObjectId: this.currentDataObjectId,
        timeseriesReference: timeseriesReference,
      })
        .then(response => {
          this.createdAlert = true;
          this.timeseriesList = [response].concat(this.timeseriesList || []);
        })
        .catch(e => {
          const error =
            "Error while creating timeseries reference: " + e.statusText;
          console.log(error);
          emitter.emit("error", error);
        });
    },
    convertDate(date: number) {
      return new Date(date).toLocaleString("en-GB", dateFormat);
    },
  },
});
</script>
