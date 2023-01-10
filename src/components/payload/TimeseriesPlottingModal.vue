<script setup lang="ts">
import VisualizationModal from "@/components/payload/VisualizationModal.vue";
import type { PlottingData } from "@/utils/plotting";
import type {
  Timeseries,
  TimeseriesPayload,
} from "@dlr-shepard/shepard-client";
import { Chart, registerables } from "chart.js";
import { computed, ref, type PropType } from "vue";

Chart.register(...registerables);

const checkedTSList = ref<TimeseriesPayload[]>([]);
const plotShown = ref(false);
const updated = ref(0);
const chartData = ref<PlottingData>({ datasets: [], xLabel: "" });
const timeseriesOptions = computed(() => {
  return props.timeseriesPayloadList.map(tsPayload => {
    return {
      value: tsPayload,
      text: getTimeseriesName(tsPayload.timeseries),
    };
  });
});

const props = defineProps({
  modalId: {
    type: String,
    default: "PlottingModal",
  },
  modalName: {
    type: String,
    default: "TimeseriesPlotting",
  },
  timeseriesPayloadList: {
    type: Array as PropType<TimeseriesPayload[]>,
    required: true,
  },
  timeseriesStartTime: {
    type: Number,
    required: true,
  },
});

function reset() {
  checkedTSList.value = [];
  plotShown.value = false;
  chartData.value = { datasets: [], xLabel: "Time in s" };
}

function createPlottableData() {
  chartData.value.datasets = [];
  checkedTSList.value.forEach(payload => {
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
          x: (Number(point.timestamp) - props.timeseriesStartTime) / 1e9,
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
</script>

<template>
  <b-modal
    :id="props.modalId"
    ref="modal"
    size="xl"
    :title="props.modalName"
    lazy
    ok-only
    ok-title="Close"
    no-close-on-backdrop
    @show="reset()"
  >
    <b-list-group>
      <b-form-select
        v-model="checkedTSList"
        class="mb-2"
        :options="timeseriesOptions"
        multiple
        :select-size="6"
      ></b-form-select>
    </b-list-group>
    <b-button
      v-if="checkedTSList.length > 0"
      v-b-modal.visualization
      variant="success"
      @click="createPlottableData()"
    >
      Show Plot
    </b-button>
    <b-button v-else :disabled="true" variant="success"> Show Plot </b-button>
    <VisualizationModal
      v-if="chartData.datasets.length > 0"
      modal-id="visualization"
      :modal-name="'Visualization of ' + props.modalName"
      :input-data="chartData"
    />
  </b-modal>
</template>
