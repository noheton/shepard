<script setup lang="ts">
import VisualizationModal from "@/components/payload/VisualizationModal.vue";
import ProcessAlert from "@/components/ProcessAlert.vue";
import TimeseriesReferenceService from "@/services/timeseriesReferenceService";
import TimeseriesService from "@/services/timeseriesService";
import { downloadFile } from "@/utils/download";
import { logError } from "@/utils/error-handling";
import { convertDate } from "@/utils/helpers";
import type { PlottingData } from "@/utils/plotting";
import type {
  ResponseError,
  Timeseries,
  TimeseriesPayload,
  TimeseriesReference,
} from "@dlr-shepard/shepard-client";
import { Chart, registerables } from "chart.js";
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

const internalState = reactive(getInitialState());
const fetchedTimeseriesPayloadList = ref<TimeseriesPayload[]>([]);
async function handleSelectedRows(selectedTimeseriesList: Timeseries[]) {
  if (
    selectedTimeseriesList.length > fetchedTimeseriesPayloadList.value.length
  ) {
    // fetch und push
    const fetchedTimeseries = fetchedTimeseriesPayloadList.value.map(
      element => element.timeseries,
    );
    const elementToAdd = selectedTimeseriesList.find(
      elementToAdd =>
        fetchedTimeseries.find(
          elementToCompare =>
            JSON.stringify(elementToAdd) == JSON.stringify(elementToCompare),
        ) == undefined,
    );
    if (elementToAdd) addTimeseriesPayload(elementToAdd);
  } else {
    // delete
    const indexOfElementToDelete = fetchedTimeseriesPayloadList.value.findIndex(
      elementToDelete =>
        selectedTimeseriesList.find(
          elementToCompare =>
            JSON.stringify(elementToDelete.timeseries) ==
            JSON.stringify(elementToCompare),
        ) == undefined,
    );
    fetchedTimeseriesPayloadList.value.splice(indexOfElementToDelete, 1);
  }
}

function addTimeseriesPayload(selectedTimeseries: Timeseries) {
  if (
    !selectedTimeseries.measurement ||
    !selectedTimeseries.device ||
    !selectedTimeseries.location ||
    !selectedTimeseries.symbolicName ||
    !selectedTimeseries.field
  )
    return;
  TimeseriesService.getTimeseries({
    timeseriesContainerId: props.timeseriesReference.timeseriesContainerId,
    measurement: selectedTimeseries.measurement,
    device: selectedTimeseries.device,
    location: selectedTimeseries.location,
    symbolicName: selectedTimeseries.symbolicName,
    field: selectedTimeseries.field,
    start: props.timeseriesReference.start,
    end: props.timeseriesReference.end,
  })
    .then(response => {
      fetchedTimeseriesPayloadList.value.push(response);
    })
    .catch(e => {
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

Chart.register(...registerables);

const plotShown = ref(false);
const updated = ref(0);
const chartData = ref<PlottingData>({ datasets: [], xLabel: "" });

const fields = [
  { key: "selected", label: "" },
  { key: "measurement", label: "Measurement" },
  { key: "location", label: "Location" },
  { key: "device", label: "Device" },
  { key: "symbolicName", label: "Symbolic Name" },
  { key: "field", label: "Field" },
];

function reset() {
  plotShown.value = false;
  chartData.value = { datasets: [], xLabel: "Time in s" };
}

function handlePlot() {
  if (fetchedTimeseriesPayloadList.value) {
    createPlottableData(fetchedTimeseriesPayloadList.value);
  }
}

function createPlottableData(selectedTimeseriesPayloads: TimeseriesPayload[]) {
  chartData.value.datasets = [];
  selectedTimeseriesPayloads.forEach(payload => {
    const data = payload.points
      .filter(point => {
        return (
          point.timestamp != undefined &&
          point.value != undefined &&
          typeof point.value == "number"
        );
      })
      .map(point => {
        return {
          x: (Number(point.timestamp) - props.timeseriesReference.start) / 1e9,
          y: Number(point.value),
        };
      });
    chartData.value.datasets.push({
      dataPoints: data,
      label: getTimeseriesName(payload.timeseries),
    });
  });
  updated.value++;
}

function getTimeseriesName(ts: Timeseries) {
  return Object.values(ts).join(" - ");
}

onMounted(() => {
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
          v-b-modal.visualization
          v-b-tooltip.hover
          title="Plotting"
          variant="primary"
          :disabled="fetchedTimeseriesPayloadList.length == 0"
          @click="handlePlot()"
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
        hover
        small
        :items="props.timeseriesReference.timeseries"
        :fields="fields"
        select-mode="multi"
        selectable
        @show="reset()"
        @row-selected="handleSelectedRows($event)"
      >
        <template #cell(selected)="{ rowSelected }">
          <template v-if="rowSelected">
            <CheckboxChecked />
            <span class="sr-only">Selected</span>
          </template>
          <template v-else>
            <CheckboxEmpty />
            <span class="sr-only">Not selected</span>
          </template>
        </template>
        <template #cell(measurement)="data">
          {{ data.item.measurement }}
        </template>

        <template #cell(location)="data">
          {{ data.item.location }}
        </template>

        <template #cell(device)="data">
          {{ data.item.device }}
        </template>

        <template #cell(symbolicName)="data">
          {{ data.item.symbolicName }}
        </template>

        <template #cell(field)="data">
          {{ data.item.field }}
        </template>
      </b-table>
    </div>

    <VisualizationModal
      v-if="chartData.datasets.length > 0"
      modal-id="visualization"
      :modal-name="'Visualization of ' + props.timeseriesReference.name"
      :input-data="chartData"
    />
  </div>
</template>
