<script setup lang="ts">
import type {
  Timeseries,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import type { ChartData, ChartDataset, ChartOptions, Point } from "chart.js";
import { useFetchTimeseriesPayload } from "~/composables/context/useFetchTimeseriesReferencePayload";

interface ShowTimeseriesReferenceDialogProps {
  collectionId: number;
  dataObjectId: number;
  timeseriesReferenceId: number;
  timeseries: Timeseries[];
}

const props = defineProps<ShowTimeseriesReferenceDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const { timeseriesWithDataPoints, isLoading } = useFetchTimeseriesPayload(
  props.collectionId,
  props.dataObjectId,
  props.timeseriesReferenceId,
);

const chartData = ref<ChartData<"line", Point[]>>({
  datasets: [],
});

const chartOptions: ChartOptions<"line"> = {
  datasets: { line: { borderWidth: 2 } },
  plugins: {
    legend: {
      align: "start",
    },
  },
  scales: {
    x: {
      title: {
        display: true,
        padding: 12,
        text: "Time (UTC)",
      },
      ticks: {
        callback: (tickValue, _index, _ticks) => {
          if (typeof tickValue == "number")
            return toDateTimeString(tickValue, dateTimeOptions);
          return tickValue;
        },
      },
      type: "linear",
    },
  },
};

const chartColors: string[] = [
  "#7ECA8F",
  "#FCA54D",
  "#B799DB",
  "#E56874",
  "#4097CC",
  "#FFD145",
  "#8C8C8C",
];

const dateTimeOptions: Intl.DateTimeFormatOptions = {
  year: "2-digit",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
};

watch(timeseriesWithDataPoints, () => {
  if (!timeseriesWithDataPoints.value) return;

  const filteredTimeseries = timeseriesWithDataPoints.value.filter(item =>
    props.timeseries.find(
      timeseries =>
        item.timeseries.device === timeseries.device &&
        item.timeseries.field === timeseries.field &&
        timeseries.location === item.timeseries.location &&
        timeseries.measurement === item.timeseries.measurement &&
        timeseries.symbolicName === item.timeseries.symbolicName,
    ),
  );
  chartData.value = {
    ...chartData.value,
    datasets: mapToDatasets(filteredTimeseries),
  };
});

function getColor(index: number): string {
  return chartColors[index % chartColors.length] ?? "#8C8C8C";
}

function mapToDatasets(
  timeseriesList: TimeseriesWithDataPoints[],
): ChartDataset<"line", Point[]>[] {
  return timeseriesList.map((timeseries, index) => {
    return {
      borderColor: getColor(index),
      label: `${timeseries.timeseries.measurement}-${timeseries.timeseries.device}-${timeseries.timeseries.location}-${timeseries.timeseries.symbolicName}-${timeseries.timeseries.field}`,
      data: timeseries.points.map(point => {
        return { x: point.timestamp, y: point.value } as Point;
      }),
    };
  });
}

function downloadChartAsImage() {
  const chart = document.getElementById(
    "timeseries-payload-chart",
  ) as HTMLCanvasElement;
  if (chart) {
    const link = document.createElement("a");
    link.download = "chart";
    link.href = chart.toDataURL("image/png");
    link.click();
  }
}
</script>

<template>
  <v-dialog v-model="showDialog" persistent :max-width="1000">
    <v-card>
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">Graph and Metrics</div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>
      <template #text>
        <div class="text-subtitle-1 pb-4">Timeseries</div>
        <div>
          <ul class="pl-8 pt-2 pb-8">
            <li v-for="item in timeseries" :key="item.symbolicName">
              {{
                `${item.device}-${item.field}-${item.location}-${item.measurement}-${item.symbolicName}`
              }}
            </li>
          </ul>
        </div>
        <div class="text-subtitle-1 pb-4">Graph</div>
        <div
          class="pa-4"
          style="background-color: rgb(var(--v-theme-divider2))"
        >
          <LineChart
            v-if="isLoading"
            id="timeseries-payload-chart"
            :data="chartData"
            :options="chartOptions"
          />
          <CenteredLoadingSpinner v-else />
        </div>
        <div class="py-4">
          <v-btn
            variant="flat"
            color="primary"
            prepend-icon="mdi-tray-arrow-down"
            text="Download as PNG"
            @click="downloadChartAsImage"
          />
        </div>
        <div class="d-flex justify-end">
          <v-btn
            variant="flat"
            color="treeview"
            text="Close"
            @click="showDialog = false"
          />
        </div>
      </template>
    </v-card>
  </v-dialog>
</template>
