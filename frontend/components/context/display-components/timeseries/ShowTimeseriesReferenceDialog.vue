<script setup lang="ts">
import type {
  ResponseError,
  SemanticAnnotation,
  Timeseries,
  TimeseriesReference,
  TimeseriesWithDataPoints,
} from "@dlr-shepard/backend-client";
import { useFetchTimeseries } from "~/composables/context/useFetchTimeseries";
import { useFetchTimeseriesAnnotations } from "~/composables/context/useFetchTimeseriesAnnotations";
import { useFetchTimeseriesPayload } from "~/composables/context/useFetchTimeseriesReferencePayload";
import type { Metrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import { useFetchTimeseriesReferenceMetrics } from "~/composables/context/useFetchTimeseriesReferencesMetrics";
import type { TimeseriesSeries } from "~/components/common/chart/types";

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
  isAllowedToEditCollection?: boolean;
}

const props = defineProps<ShowTimeseriesReferenceDialogProps>();
const showDialog = defineModel<boolean>("showDialog", {
  required: true,
  default: false,
});

const timeseriesMetrics = ref<Record<string, TimeseriesMetrics>>({});

const metricsNames: Record<string, string> = {
  MIN: "Min",
  MAX: "Max",
  STDDEV: "Std dev",
  COUNT: "Count",
  MEAN: "Mean",
  MEDIAN: "Median",
  FIRST: "First",
  LAST: "Last",
  FREQUENCY: "Frequency",
};

const { timeseriesWithDataPoints, isLoading } = useFetchTimeseriesPayload(
  props.collectionId,
  props.dataObjectId,
  props.timeseriesReferenceId,
);

function getTimeseriesKey(ts: Timeseries): string {
  return `${ts.measurement}-${ts.device}-${ts.location}-${ts.symbolicName}-${ts.field}`;
}

function channelLabel(ts: Timeseries): string {
  const parts = [ts.device, ts.field, ts.location, ts.measurement, ts.symbolicName]
    .filter(Boolean);
  return parts.join(" · ");
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
        newTimeseries.map(async ts => {
          const metrics = await useFetchTimeseriesReferenceMetrics(
            props.collectionId,
            props.dataObjectId,
            props.timeseriesReferenceId,
            ts.measurement,
            ts.device,
            ts.location,
            ts.symbolicName,
            ts.field,
          );
          if (metrics?.COUNT && props.timeseriesReference) {
            const spanSeconds =
              (props.timeseriesReference.end - props.timeseriesReference.start) / 1e9;
            const freq = parseFloat(metrics.COUNT) / spanSeconds;
            metrics.FREQUENCY = `${toFormattedDouble(freq.toString(), 9)} Hz`;
          }

          const timeseriesObj = await useFetchTimeseries(
            props.timeseriesContainerId,
            ts.measurement,
            ts.device,
            ts.location,
            ts.symbolicName,
            ts.field,
          );

          const annotations =
            timeseriesObj?.id
              ? await useFetchTimeseriesAnnotations(
                  props.timeseriesContainerId,
                  timeseriesObj.id,
                )
              : [];

          return [getTimeseriesKey(ts), { metrics, annotations }] as const;
        }),
      );
      timeseriesMetrics.value = Object.fromEntries(data);
    } catch (error) {
      handleError(error as ResponseError, "fetching timeseries metrics");
      timeseriesMetrics.value = {};
    }
  },
  { immediate: true },
);

// ── chart series ──────────────────────────────────────────────────────────────

const chartColors: string[] = [
  "#7ECA8F", "#FCA54D", "#B799DB", "#E56874",
  "#4097CC", "#FFD145", "#8C8C8C", "#F06292",
];

function getColor(index: number): string {
  return chartColors[index % chartColors.length] ?? "#8C8C8C";
}

// channels enabled in chart — default all on
const enabledKeys = ref<Set<string>>(new Set());
watch(
  () => props.timeseries,
  ts => {
    enabledKeys.value = new Set(ts.map(getTimeseriesKey));
  },
  { immediate: true },
);

function toggleChannel(key: string) {
  if (enabledKeys.value.has(key)) {
    enabledKeys.value.delete(key);
  } else {
    enabledKeys.value.add(key);
  }
  // trigger reactivity on Set mutation
  enabledKeys.value = new Set(enabledKeys.value);
}

function mapToSeries(items: TimeseriesWithDataPoints[]): TimeseriesSeries[] {
  return items
    .map((item, idx) => {
      const key = getTimeseriesKey(item.timeseries);
      return {
        key,
        name: channelLabel(item.timeseries),
        color: getColor(idx),
        data: item.points.map(p => [p.timestamp, p.value] as [number, number]),
      };
    })
    .filter(s => enabledKeys.value.has(s.key));
}

const chartSeries = computed<TimeseriesSeries[]>(() => {
  if (!timeseriesWithDataPoints.value) return [];
  const filtered = timeseriesWithDataPoints.value.filter(item =>
    props.timeseries.some(
      ts =>
        ts.device === item.timeseries.device &&
        ts.field === item.timeseries.field &&
        ts.location === item.timeseries.location &&
        ts.measurement === item.timeseries.measurement &&
        ts.symbolicName === item.timeseries.symbolicName,
    ),
  );
  return mapToSeries(filtered);
});

// ── expanded metrics rows ─────────────────────────────────────────────────────
const expandedMetrics = ref<Set<string>>(new Set());
function toggleMetrics(key: string) {
  if (expandedMetrics.value.has(key)) expandedMetrics.value.delete(key);
  else expandedMetrics.value.add(key);
  expandedMetrics.value = new Set(expandedMetrics.value);
}
</script>

<template>
  <v-dialog
    v-if="timeseriesReference"
    v-model="showDialog"
    persistent
    :max-width="1080"
  >
    <v-card>
      <template #title>
        <div class="d-flex justify-space-between align-baseline">
          <div class="text-h4">Graph &amp; Metrics</div>
          <v-btn
            variant="plain"
            density="compact"
            icon="mdi-close"
            @click="showDialog = false"
          />
        </div>
      </template>

      <template #text>
        <!-- chart ─────────────────────────────────────────────────────────── -->
        <div class="mb-4" style="position: relative">
          <CenteredLoadingSpinner v-if="isLoading" />
          <TimeseriesChart
            v-else
            :series="chartSeries"
            :x-min="timeseriesReference ? timeseriesReference.start / 1e6 : undefined"
            :x-max="timeseriesReference ? timeseriesReference.end / 1e6 : undefined"
            show-legend
            height="340px"
          />
        </div>

        <!-- channel list ─────────────────────────────────────────────────── -->
        <div class="text-subtitle-1 mb-2">Channels</div>
        <div
          v-for="(ts, idx) in timeseries"
          :key="getTimeseriesKey(ts)"
          class="channel-row mb-1"
        >
          <!-- header row: color swatch + checkbox + label + expand metrics -->
          <div class="d-flex align-center">
            <div
              class="color-swatch mr-2 flex-shrink-0"
              :style="{ background: getColor(idx) }"
            />
            <v-checkbox
              :model-value="enabledKeys.has(getTimeseriesKey(ts))"
              density="compact"
              hide-details
              class="flex-shrink-0 mr-1"
              @update:model-value="toggleChannel(getTimeseriesKey(ts))"
            />
            <span
              class="text-body-2 flex-grow-1"
              style="cursor: pointer"
              @click="toggleChannel(getTimeseriesKey(ts))"
            >{{ channelLabel(ts) }}</span>
            <v-btn
              density="compact"
              variant="text"
              size="small"
              :icon="expandedMetrics.has(getTimeseriesKey(ts)) ? 'mdi-chevron-up' : 'mdi-chevron-down'"
              @click="toggleMetrics(getTimeseriesKey(ts))"
            />
          </div>

          <!-- expanded: metrics grid + annotations -->
          <div
            v-if="expandedMetrics.has(getTimeseriesKey(ts))"
            class="ml-10 mt-1 mb-2"
          >
            <div class="metrics-grid text-body-2 text-medium-emphasis mb-2">
              <template
                v-for="(val, key) in timeseriesMetrics[getTimeseriesKey(ts)]?.metrics"
                :key="key"
              >
                <span class="metric-label">{{ metricsNames[key] ?? key }}:</span>
                <span class="metric-value">{{ val }}</span>
              </template>
            </div>
            <div class="d-flex flex-wrap gap-1">
              <SemanticAnnotationChip
                v-for="annotation in timeseriesMetrics[getTimeseriesKey(ts)]?.annotations"
                :key="annotation.id"
                :can-delete="!!isAllowedToEditCollection"
                :annotation="annotation"
                :annotated-type="
                  new AnnotatedReference(
                    collectionId,
                    dataObjectId,
                    props.timeseriesReferenceId,
                  )
                "
              />
            </div>
          </div>
        </div>

        <div class="d-flex justify-end mt-4">
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
.color-swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 8px;
  row-gap: 2px;
}

.metric-label {
  font-weight: 500;
  white-space: nowrap;
}
</style>
