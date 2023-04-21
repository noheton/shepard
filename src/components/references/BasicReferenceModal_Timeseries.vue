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
import Vue, { computed, onMounted, reactive, ref, type PropType } from "vue";

Chart.register(...registerables);

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

const chartData = ref<PlottingData>({ datasets: [], xLabel: "" });

const fields = [
  { key: "isSelected", sortable: true },
  { key: "measurement", label: "Measurement", sortable: true },
  { key: "location", label: "Location", sortable: true },
  { key: "device", label: "Device", sortable: true },
  { key: "symbolicName", label: "Symbolic Name", sortable: true },
  { key: "field", label: "Field", sortable: true },
];

function fetchTimeseriesPayload(selectedTimeseries: Timeseries) {
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
      addToChartData(response);
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

function addToChartData(payload: TimeseriesPayload) {
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

function getTimeseriesName(ts: Timeseries) {
  return Object.values(ts).join(" - ");
}

interface TimeseriesSelectable extends Timeseries {
  isSelected: boolean;
}

const filter = ref<string>("");
const filteredItemsList = ref<TimeseriesSelectable[]>([]);
const perPage = 12;
const currentPage = ref(1);

const totalRows = computed(() => {
  return filter.value
    ? filteredItemsList.value.length
    : props.timeseriesReference.timeseries.length;
});

function onRowClicked(selectedTimeseries: TimeseriesSelectable) {
  // to remember which timeseries is selected we need an additional boolean.
  // this functionality is not supported by bootstrap tables
  Vue.set(selectedTimeseries, "isSelected", !selectedTimeseries.isSelected);
  if (selectedTimeseries.isSelected) {
    fetchTimeseriesPayload(selectedTimeseries);
  } else {
    // delete from chartData
    const indexOfDatasetToDelete = chartData.value.datasets.findIndex(
      dataset => getTimeseriesName(selectedTimeseries) == dataset.label,
    );
    chartData.value.datasets.splice(indexOfDatasetToDelete, 1);
  }
}

function onTableFiltered(filteredItems: TimeseriesSelectable[]) {
  filteredItemsList.value = filteredItems;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function slotScopeIsSelected(slotScope: any) {
  return "value" in slotScope ? slotScope.value : false;
}

onMounted(() => {
  chartData.value = { datasets: [], xLabel: "Time in s" };
  Object.assign(internalState, getInitialState());
  // clear remembered timeseries see "onRowClicked"
  props.timeseriesReference.timeseries.forEach(e => {
    Vue.set(e, "isSelected", false);
  });
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
          :disabled="chartData.datasets.length == 0"
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

      <b-row class="mt-2">
        <b-col>
          <b-input v-model="filter" placeholder="Filter.."></b-input>
        </b-col>
      </b-row>
      <b-table
        class="mt-1"
        select-mode="multi"
        selectable
        hover
        small
        :fields="fields"
        :items="props.timeseriesReference.timeseries"
        :per-page="perPage"
        :current-page="currentPage"
        :filter="filter"
        @filtered="onTableFiltered($event)"
        @row-clicked="onRowClicked"
      >
        <template #cell(isSelected)="data">
          <CheckboxChecked v-if="slotScopeIsSelected(data)" />
          <CheckboxEmpty v-else />
        </template>
      </b-table>
    </div>
    <b-pagination
      v-model="currentPage"
      :total-rows="totalRows"
      :per-page="perPage"
      align="right"
    ></b-pagination>

    <VisualizationModal
      v-if="chartData.datasets.length > 0"
      modal-id="visualization"
      :modal-name="'Visualization of ' + props.timeseriesReference.name"
      :input-data="chartData"
    />
  </div>
</template>
