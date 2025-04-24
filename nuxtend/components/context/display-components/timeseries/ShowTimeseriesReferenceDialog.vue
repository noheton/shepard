<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
  Timeseries,
  TimeseriesReference,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import type { ChartData, ChartDataset, ChartOptions, Point } from "chart.js";
import { useFetchTimeseries } from "~/composables/context/useFetchTimeseries";
import { useFetchTimeseriesAnnotations } from "~/composables/context/useFetchTimeseriesAnnotations";
import { useFetchTimeseriesPayload } from "~/composables/context/useFetchTimeseriesReferencePayload";
import type { Metrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import { useFetchTimeseriesReferenceMetrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";

type TimeseriesMetrics = {
  metrics?: Metrics;
  annotations: SemanticAnnotation[];
};

interface ShowTimeseriesReferenceDialogProps {
  collectionId: number;
  dataObjectId: number;
  timeseriesReferenceId: number;
  timeseriesContainerId: number;
  timeseries: Timeseries[];
  timeseriesReference?: TimeseriesReference;
}

const props = defineProps<ShowTimeseriesReferenceDialogProps>();
const expanded = ref<boolean[]>([]);
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const timeseriesMetrics = ref<Record<string, TimeseriesMetrics>>({});

const metricsNames = {
  MIN: "Min. value",
  MAX: "Max. value",
  STDDEV: "Standard deviation",
  COUNT: "No. of measurements",
  MEAN: "Mean",
  MEDIAN: "Median",
  FIRST: "First value",
  LAST: "Last value",
  FREQUENCY: "Frequency",
};

const { timeseriesWithDataPoints, isLoading } = useFetchTimeseriesPayload(
  props.collectionId,
  props.dataObjectId,
  props.timeseriesReferenceId,
);

function getTimeseriesUniqueKey(aTimeseries: Timeseries): string {
  return `${aTimeseries.measurement}-${aTimeseries.device}-${aTimeseries.location}-${aTimeseries.symbolicName}-${aTimeseries.field}`;
}

watch(
  () => props.timeseries,
  async newTimeseries => {
    if (!newTimeseries || newTimeseries.length === 0) {
      timeseriesMetrics.value = {};
      return;
    }
    try {
      const data = await Promise.all(
        newTimeseries.map(async aTimeseries => {
          const timeseriesReferenceMetrics =
            await useFetchTimeseriesReferenceMetrics(
              props.collectionId,
              props.dataObjectId,
              props.timeseriesReferenceId,
              aTimeseries.measurement,
              aTimeseries.device,
              aTimeseries.location,
              aTimeseries.symbolicName,
              aTimeseries.field,
            );
          if (
            timeseriesReferenceMetrics &&
            timeseriesReferenceMetrics.COUNT &&
            props.timeseriesReference
          ) {
            const timeSpanInSeconds =
              (props.timeseriesReference.end -
                props.timeseriesReference.start) /
              1e9;
            const frequency =
              parseFloat(timeseriesReferenceMetrics.COUNT) / timeSpanInSeconds;
            timeseriesReferenceMetrics.FREQUENCY = `${toFormattedDouble(frequency.toString(), 9)} Hz`;
          }

          const timeseries = await useFetchTimeseries(
            props.timeseriesContainerId,
            aTimeseries.measurement,
            aTimeseries.device,
            aTimeseries.location,
            aTimeseries.symbolicName,
            aTimeseries.field,
          );

          const annotations =
            timeseries && timeseries.id
              ? await useFetchTimeseriesAnnotations(
                  props.timeseriesContainerId,
                  timeseries.id,
                )
              : [];

          const timeseriesMetrics: TimeseriesMetrics = {
            metrics: timeseriesReferenceMetrics,
            annotations: annotations,
          };
          return [
            getTimeseriesUniqueKey(aTimeseries),
            timeseriesMetrics,
          ] as const;
        }),
      );

      timeseriesMetrics.value = Object.fromEntries(data);
    } catch (error) {
      handleError(
        error as ResponseError,
        "fetching timeseriesReference metrics",
      );
      timeseriesMetrics.value = {};
    }
  },
  { immediate: true },
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

function toggle(index: number) {
  expanded.value[index] = !expanded.value[index];
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
  <v-dialog
    v-if="timeseriesReference"
    v-model="showDialog"
    persistent
    :max-width="1000"
  >
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
        <div class="text-subtitle-1 pb-4">Metrics</div>

        <div v-for="(item, index) in timeseries" :key="index">
          <div
            style="cursor: pointer; display: flex; align-items: center"
            class="pt-1 pb-1"
            @click="toggle(index)"
          >
            <v-icon
              :icon="expanded[index] ? 'mdi-chevron-down' : 'mdi-chevron-right'"
              class="mr-2"
            />

            {{
              `${item.device} - ${item.field} - ${item.location} - ${item.measurement} - ${item.symbolicName}`
            }}
          </div>
          <div v-if="expanded[index]" style="margin-left: 20px">
            <v-row no-gutters>
              <v-col cols="auto" class="d-flex align-center justify-center">
                <div
                  style="width: 25px; height: 25px; margin: 20px"
                  :style="{ backgroundColor: getColor(index) }"
                />
              </v-col>
              <v-col class="grid-container">
                <div
                  v-for="(metric, key) in timeseriesMetrics[
                    getTimeseriesUniqueKey(item)
                  ]?.metrics"
                  :key="key"
                  class="column text-body-2 text-medium-emphasis"
                >
                  {{ metricsNames[key] }}:
                  {{ metric }}
                </div>
              </v-col>
            </v-row>
            <v-row class="pa-2 pb-3">
              <SemanticAnnotationChip
                v-for="annotation in timeseriesMetrics[
                  getTimeseriesUniqueKey(item)
                ]?.annotations"
                :key="annotation.id"
                :annotation="annotation"
                :annotated-type="
                  new AnnotatedReference(
                    collectionId,
                    dataObjectId,
                    props.timeseriesReferenceId,
                  )
                "
              />
            </v-row>
          </div>
        </div>
        <div class="text-subtitle-1 pb-4 pt-4">Graph</div>
        <div class="pa-4" style="background-color: white">
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

<style scoped>
.grid-container {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.item {
  background: #f0f0f0;
  padding: 10px;
  text-align: center;
  border-radius: 8px;
}
</style>
