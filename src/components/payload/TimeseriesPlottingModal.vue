<script setup lang="ts">
import { HSVtoRGB } from "@/utils/colors";
import type {
  Timeseries,
  TimeseriesPayload,
} from "@dlr-shepard/shepard-client";
import {
  Chart,
  registerables,
  type ChartData,
  type ScatterDataPoint,
} from "chart.js";
import { computed, ref, type PropType } from "vue";
import { Scatter } from "vue-chartjs";

Chart.register(...registerables);

const chartOptions = {
  datasets: { scatter: { showLine: true, tension: 0.1 } },
  responsive: true,
  maintainAspectRatio: false,
  scales: {
    x: {
      title: {
        display: true,
        text: "Time in s",
      },
    },
    y: {
      title: {
        display: true,
        text: "Value",
      },
    },
  },
};

const props = defineProps({
  modalId: {
    type: String,
    default: "PlottingModal",
  },
  modalName: {
    type: String,
    default: "PlottingModal",
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

const chartData = ref<
  ChartData<"scatter", (number | ScatterDataPoint | null)[], unknown>
>({
  datasets: [],
});
const checkedTSList = ref<TimeseriesPayload[]>([]);
const plotShown = ref(false);
const colorCounter = ref(0);
const updated = ref(0);

const timeseriesOptions = computed(() => {
  return props.timeseriesPayloadList.map(tsPayload => {
    return {
      value: tsPayload,
      text: getTimeseriesName(tsPayload.timeseries),
    };
  });
});

function plotData() {
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
    const colorSetting = colorCalculator(colorCounter.value);
    chartData.value.datasets.push({
      label: getTimeseriesName(payload.timeseries),
      fill: false,
      borderColor: colorSetting,
      backgroundColor: colorSetting,
      data: data,
    });
    colorCounter.value++;
  });
  colorCounter.value = 0;
  plotShown.value = true;
  updated.value++;
}

function getTimeseriesName(ts: Timeseries) {
  return Object.values(ts).join(" - ");
}

function colorCalculator(counter: number) {
  const baseColorArray = [
    [202, 1, 0.733],
    [1, 0.659, 0.702],
  ];
  let colorIndex = 0;
  const returnColor = baseColorArray[colorIndex];
  if (counter < 3) {
    returnColor[1] = returnColor[1] - counter * 0.4;
  } else {
    colorIndex = 1;
    returnColor[1] = returnColor[1] - (counter - 3) * 0.3;
    if (counter == 5) {
      colorCounter.value = -1;
    }
  }
  return HSVtoRGB(returnColor);
}

function reset() {
  chartData.value = {
    datasets: [],
  };
  checkedTSList.value = [];
  colorCounter.value = 0;
  plotShown.value = false;
}

function savePlot() {
  // saveData() inspired by https://github.com/apertureless/vue-chartjs/issues/89#issuecomment-292718708
  const plottingImage = document.getElementById(
    "scatter-chart",
  ) as HTMLCanvasElement | null;
  if (plottingImage != null) {
    const link = document.createElement("a");
    link.download = props.modalName.replace(/[<>:"/\\|?* ]/g, "_");
    link.href = plottingImage.toDataURL("image/png");
    link.click();
  }
}
</script>

<template>
  <b-modal
    :id="modalId"
    ref="modal"
    size="xl"
    :title="modalName"
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
      v-b-tooltip.hover
      title="Show Plot"
      variant="primary"
      @click="plotData()"
    >
      Show Plot
    </b-button>
    <b-button
      id="exportButton"
      v-b-tooltip.hover
      title="Save Plot to .PNG"
      variant="secondary"
      class="ml-1"
      :disabled="!plotShown"
      @click="savePlot()"
    >
      Save Plot
    </b-button>
    <div class="plot">
      <Scatter
        v-if="chartData.datasets.length > 0"
        :key="updated"
        :options="chartOptions"
        :data="chartData"
        chart-id="scatter-chart"
        dataset-id-key="label"
      />
    </div>
  </b-modal>
</template>

<style scoped>
.plot {
  height: 400px;
  position: "relative";
}
</style>
