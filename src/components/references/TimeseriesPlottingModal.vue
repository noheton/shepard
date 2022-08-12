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
    <div class="plot">
      <b-button
        v-b-tooltip.hover
        title="Show Plot"
        variant="success"
        @click="plotData()"
      >
        Show Plot
      </b-button>
      <b-button
        id="exportButton"
        v-b-tooltip.hover
        title="Save Plot to .PNG"
        variant="success"
        class="ml-1"
        :disabled="!plotShown"
        @click="savePlot()"
      >
        Save Plot
      </b-button>
      <Scatter
        v-if="chartData.datasets.length > 0"
        :chart-options="chartOptions"
        :chart-data="chartData"
        chart-id="scatter-chart"
        dataset-id-key="label"
      />
    </div>
  </b-modal>
</template>

<script lang="ts">
import { HSVtoRGB } from "@/utils/colors";
import { dateFormat } from "@/utils/helpers";
import type {
  Timeseries,
  TimeseriesPayload,
} from "@dlr-shepard/shepard-client";
import {
  Chart,
  registerables,
  type ChartData,
  type ChartOptions,
} from "chart.js";
import { defineComponent, type PropType } from "vue";
import { Scatter } from "vue-chartjs/legacy";

Chart.register(...registerables);

interface TimeseriesPlottingModalData {
  chartData: ChartData;
  chartOptions: ChartOptions;
  buttonPressed: boolean;
  checkedTSList: TimeseriesPayload[];
  counter: number;
  returnColor: number[];
  plotShown: boolean;
}

function initialState(): TimeseriesPlottingModalData {
  return {
    buttonPressed: false,
    chartData: {
      datasets: [],
    },
    chartOptions: {
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
    },
    checkedTSList: [],
    counter: 0,
    returnColor: [],
    plotShown: false,
  };
}

export default defineComponent({
  components: { Scatter },
  props: {
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
  },
  data() {
    return initialState();
  },
  computed: {
    timeseriesOptions(): { value: TimeseriesPayload; text: string }[] {
      return this.timeseriesPayloadList.map(tsPayload => {
        return {
          value: tsPayload,
          text: this.getTimeseriesName(tsPayload.timeseries),
        };
      });
    },
  },
  methods: {
    getTimeseriesName(ts: Timeseries) {
      return Object.values(ts).join(" - ");
    },
    convertDate(date: number) {
      return new Date(date).toLocaleString("en-GB", dateFormat);
    },
    colorCalculator(counter: number) {
      const baseColorArray = [
        [202, 1, 0.733],
        [1, 0.659, 0.702],
      ];
      let colorIndex = 0;
      if (counter < 3) {
        this.returnColor = baseColorArray[colorIndex];
        this.returnColor[1] = this.returnColor[1] - counter * 0.4;
      } else {
        colorIndex = 1;
        this.returnColor = baseColorArray[colorIndex];
        this.returnColor[1] = this.returnColor[1] - (counter - 3) * 0.3;
        if (counter == 5) {
          this.counter = -1;
        }
      }
      return HSVtoRGB(this.returnColor);
    },
    plotData() {
      this.chartData.datasets = [];
      this.checkedTSList.forEach(payload => {
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
              x: (Number(point.timestamp) - this.timeseriesStartTime) / 1e9,
              y: Number(point.value),
            };
          });
        const colorSetting = this.colorCalculator(this.counter);
        this.chartData.datasets.push({
          label: this.getTimeseriesName(payload.timeseries),
          fill: false,
          borderColor: colorSetting,
          backgroundColor: colorSetting,
          data: data,
        });
        this.counter = this.counter + 1;
      });
      this.counter = 0;
      this.plotShown = true;
    },
    reset() {
      Object.assign(this.$data, initialState());
    },
    savePlot() {
      // saveData() inspired by https://github.com/apertureless/vue-chartjs/issues/89#issuecomment-292718708
      const plottingImage = document.getElementById(
        "scatter-chart",
      ) as HTMLCanvasElement | null;
      if (plottingImage != null) {
        const link = document.createElement("a");
        link.download = this.modalName.replace(/[<>:"/\\|?* ]/g, "_");
        link.href = plottingImage.toDataURL("image/png");
        link.click();
      }
    },
  },
});
</script>
