<script setup lang="ts">
import { colorCalculator } from "@/utils/colors";
import type { PlottingData } from "@/utils/plotting";
import {
  Chart,
  registerables,
  type ChartData,
  type ScatterDataPoint,
} from "chart.js";
import type { PropType } from "vue";
import { computed, ref } from "vue";
import { Scatter } from "vue-chartjs";

Chart.register(...registerables);
const updated = ref(0);
const colorCounter = ref<number>(0);
const chartData = ref<
  ChartData<"scatter", (number | ScatterDataPoint | null)[], unknown>
>({ datasets: [] });

const props = defineProps({
  modalId: {
    type: String,
    default: "VisualizationOfData",
  },
  modalName: {
    type: String,
    default: "Your Data",
  },
  inputData: {
    type: Object as PropType<PlottingData>,
    required: true,
  },
});

const chartOptions = computed(() => {
  return {
    datasets: { scatter: { showLine: true, tension: 0.1 } },
    responsive: true,
    maintainAspectRatio: false,
    animation: {
      duration: 0,
    },
    scales: {
      x: {
        title: {
          display: true,
          text: props.inputData.xLabel,
        },
      },
      y: {
        title: {
          display: true,
          text: "y Value",
        },
      },
    },
  };
});

function reset() {
  colorCounter.value = 0;
  updated.value = 0;
}

function savePlot() {
  // savePlot() inspired by https://github.com/apertureless/vue-chartjs/issues/89#issuecomment-292718708
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

function prepareDataForVisualization() {
  chartData.value.datasets = [];
  props.inputData.datasets.forEach(dataSet => {
    const colorSetting = colorCalculator(colorCounter.value % 6);
    colorCounter.value++;
    chartData.value.datasets.push({
      data: dataSet.dataPoints,
      label: dataSet.label,
      fill: false,
      borderColor: colorSetting,
      backgroundColor: colorSetting,
    });
  });
  updated.value++;
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
    @show="
      prepareDataForVisualization();
      reset();
    "
  >
    <div class="plot">
      <Scatter
        v-if="props.inputData"
        id="scatter-chart"
        :key="updated"
        :options="chartOptions"
        :data="chartData"
        dataset-id-key="label"
      />
    </div>
    <b-button
      v-b-tooltip.hover
      title="Save Plot to .PNG"
      variant="secondary"
      class="ml-1"
      @click="savePlot()"
    >
      Save Plot
    </b-button>
  </b-modal>
</template>

<style scoped>
.plot {
  height: 400px;
  position: "relative";
}
</style>
